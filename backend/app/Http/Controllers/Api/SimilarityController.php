<?php

namespace App\Http\Controllers\Api;

use App\Models\Project;
use App\Models\Similarity;
use App\Models\MotorConfig;
use App\Services\SimilitudService;
use App\Similarity\Recomputador;
use App\Http\Controllers\Controller;
use Illuminate\Database\Eloquent\Builder;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;

class SimilarityController extends Controller
{
    // ---------------------------------------------------------------- CRUD

    public function index(Request $request)
    {
        // Params inválidos (p. ej. arrays) se rechazan con 422, nunca 500.
        $request->validate([
            'proyecto_id' => 'nullable|integer',
            'related_to' => 'nullable|string',
            'search' => 'nullable|string',
            'ficha_id' => 'nullable|string',
            'programa' => 'nullable|string',
        ]);

        $user = $request->user();

        // Vista de UNA propuesta (revisión, análisis, detalle): pares de esa
        // propuesta donde la CONTRAPARTE está aprobada (regla de perspectiva).
        $proyectoId = $request->query('proyecto_id');
        if ($proyectoId) {
            // Vista: propia/de la ficha o contraparte de una similitud autorizada
            // (la gestión sigue siendo de propietario/equipo/admin).
            $visible = Project::where('id', $proyectoId)->visiblePara($user)->exists();
            if (!$visible) {
                return response()->json(['message' => 'No tienes acceso a esa propuesta.'], 403);
            }

            // Si es solo contraparte (no propia), se limita a los pares que tocan
            // una propuesta del usuario: no se exponen otras coincidencias ajenas.
            $esPropia = Project::where('id', $proyectoId)->deAutor($user)->exists();

            $query = Similarity::included()
                ->where(function (Builder $q) use ($proyectoId) {
                    $q->where('id_proyecto_1', $proyectoId)->orWhere('id_proyecto_2', $proyectoId);
                });

            // Evidencia de versiones anteriores (solo lectura): el contenido
            // analizado ya no es el vigente, así que no aplica la regla de
            // contraparte aprobada; manda la visibilidad del par.
            if ($request->boolean('historial')) {
                return $query->historicos()->visibleDetalle($user)->orderByDesc('id')->get();
            }

            $consulta = $query->vigentes()->contraparteAprobada((int) $proyectoId);
            if (!$esPropia) {
                $consulta = $consulta->visibleDetalle($user);
            }
            return $consulta->get();
        }

        return Similarity::included()
            ->vigentes()
            ->paraUsuario($user)
            ->relatedTo($request->query('related_to'))
            ->search($request->query('search'))
            ->byFicha($request->query('ficha_id'))
            ->byPrograma($request->query('programa'))
            ->get();
    }

    public function store(Request $request)
    {
        $request->validate([
            'porcentaje' => 'required|numeric|min:0|max:100',
            'detalles' => 'nullable|array',
            'fecha' => 'nullable|date',
            'id_proyecto_1' => 'required|exists:projects,id',
            'id_proyecto_2' => 'required|exists:projects,id',
        ]);

        if ((int) $request->id_proyecto_1 === (int) $request->id_proyecto_2) {
            return response()->json(['message' => 'Una propuesta no se puede comparar consigo misma.'], 422);
        }

        // Whitelist: el estado de vigencia lo controla el motor, no el cliente.
        // D-2b: la creación manual comparte el mutex del motor para serializarse
        // con las detecciones y los borrados; ningún par confirmado puede colarse
        // entre la comprobación y el borrado de una propuesta.
        $item = DB::transaction(function () use ($request) {
            // Inicialización canónica (igual que Recomputador): garantiza la fila
            // del motor también en el arranque y toma SIEMPRE el mutex.
            $config = MotorConfig::firstOrCreate([], ['umbral' => 0.30, 'meses' => 12]);
            MotorConfig::whereKey($config->id)->lockForUpdate()->first();

            return Similarity::create($request->only([
                'porcentaje', 'detalles', 'fecha', 'id_proyecto_1', 'id_proyecto_2',
            ]));
        });
        return response()->json($item, 201);
    }

    public function show(Request $request, $id)
    {
        $item = Similarity::included()->findOrFail($id);

        // Mismo criterio de alcance (perspectiva del rol).
        $visible = Similarity::where('id', $item->id)
            ->visibleDetalle($request->user())
            ->exists();
        if (!$visible) {
            return response()->json(['message' => 'No tienes acceso a esta similitud.'], 403);
        }

        return $item;
    }

    public function update(Request $request, Similarity $similarity)
    {
        $request->validate([
            'porcentaje' => 'required|numeric|min:0|max:100',
            'detalles' => 'nullable|array',
            'fecha' => 'nullable|date',
            'id_proyecto_1' => 'required|exists:projects,id',
            'id_proyecto_2' => 'required|exists:projects,id',
        ]);

        if ((int) $request->id_proyecto_1 === (int) $request->id_proyecto_2) {
            return response()->json(['message' => 'Una propuesta no se puede comparar consigo misma.'], 422);
        }

        // Whitelist: el estado de vigencia lo controla el motor, no el cliente.
        $similarity->update($request->only([
            'porcentaje', 'detalles', 'fecha', 'id_proyecto_1', 'id_proyecto_2',
        ]));
        return $similarity;
    }

    public function destroy(Similarity $similarity)
    {
        $similarity->delete();
        return $similarity;
    }

    // ---------------------------------------------------------------- Motor

    // Detecta coincidencias de UNA propuesta contra el corpus de APROBADAS del
    // mismo programa (dentro de la ventana) y sincroniza la tabla
    // `similarities`. Devuelve cuántos pares nuevos quedaron.
    public function detect(Request $request)
    {
        $request->validate(['id_proyecto' => 'required|exists:projects,id']);
        $propio = Project::with('classGroup')->findOrFail($request->id_proyecto);

        // Solo el dueño (aprendiz/equipo), su instructor o un admin disparan el motor.
        if (!$this->puedeDetectar($request->user(), $propio)) {
            return response()->json(['message' => 'No puedes analizar una propuesta que no es tuya.'], 403);
        }

        return response()->json(['detectadas' => app(Recomputador::class)->detectar($propio)]);
    }

    // Recalibra toda la base con el umbral/ventana vigentes: purga los pares
    // que ya no cumplen y re-ejecuta la detección sobre cada propuesta vigente.
    public function recalculate()
    {
        $r = app(Recomputador::class)->recalcular(true, true);

        return response()->json(['eliminadas' => $r['eliminadas'], 'creadas' => $r['creadas']]);
    }

    // ---------------------------------------------------------------- Demo pública

    // Compara un texto libre contra el corpus vigente sin persistir nada.
    // La landing lo usa para que cualquier visitante pruebe el motor.
    // Devuelve solo títulos y porcentajes: nunca autores ni resúmenes.
    public function demo(Request $request)
    {
        $request->validate(['texto' => 'required|string|min:3|max:300']);

        $config = $this->config();
        $umbral = (float) ($config->umbral ?? 0.30);
        $meses = (int) ($config->meses ?? 12);

        // Proyecto "sonda" virtual (id centinela 0; el corpus usa ids >= 1).
        $sonda = new Project([
            'titulo' => $request->texto,
            'palabras_clave' => $request->texto,
            'area_aplicacion' => '',
            'resumen' => '',
        ]);
        $sonda->id = 0;

        $corpus = Project::where('estado', 'aprobado')
            ->where('created_at', '>=', now()->subMonths($meses))
            ->get();

        $pares = SimilitudService::puntaje($sonda, array_merge([$sonda], $corpus->all()));
        usort($pares, fn ($a, $b) => $b['score'] <=> $a['score']);

        $titulo = $corpus->pluck('titulo', 'id');
        $coincidencias = array_map(fn ($p) => [
            'id' => $p['project_id'],
            'titulo' => $titulo[$p['project_id']] ?? 'Propuesta',
            'porcentaje' => $p['porcentaje'],
        ], array_slice($pares, 0, 3));

        return response()->json([
            'umbral' => $umbral,
            'meses' => $meses,
            'total' => $corpus->count(),
            'sobre' => count(array_filter($pares, fn ($p) => $p['score'] >= $umbral)),
            'coincidencias' => $coincidencias,
        ]);
    }

    // ---------------------------------------------------------------- Internos

    // Misma regla que la escritura: admin siempre; aprendiz solo dentro de su
    // ficha activa (creador o equipo); instructor de la ficha o asignado.
    private function puedeDetectar($user, Project $project): bool
    {
        return $project->puedeEscribir($user);
    }

    // Config global del motor (fila única).
    private function config(): MotorConfig
    {
        return MotorConfig::firstOrCreate([], ['umbral' => 0.30, 'meses' => 12]);
    }

}
