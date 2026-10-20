<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Builder;

class Similarity extends Model
{
    use HasFactory;

    // Relaciones en camelCase en el JSON (el frontend es JS).
    public static $snakeAttributes = false;

    protected $fillable = ['porcentaje', 'detalles', 'fecha', 'id_proyecto_1', 'id_proyecto_2', 'vigente'];

    // Un par siempre se guarda con el id menor primero: evita duplicados
    // invertidos (que la unique no frenaría) y hace determinista `buscarPar`.
    protected static function booted(): void
    {
        static::saving(function (Similarity $s) {
            $a = $s->id_proyecto_1;
            $b = $s->id_proyecto_2;
            if ($a && $b && (int) $a > (int) $b) {
                $s->id_proyecto_1 = $b;
                $s->id_proyecto_2 = $a;
            }
        });
    }

    // Normaliza cualquier fecha ISO/datetime a la columna `date`.
    public function setFechaAttribute($valor): void
    {
        $this->attributes['fecha'] = $valor ? substr((string) $valor, 0, 10) : null;
    }

    protected $casts = [
        'detalles' => 'array',
        'vigente' => 'boolean',
    ];

    protected $allowIncluded = [
        'project1',
        'project2',
        'project1.classGroup.program',
        'project2.classGroup.program',
        'project1.creator',
        'project2.creator',
        'project1.apprentices.generalUser',
        'project2.apprentices.generalUser',
    ];

    // Filtra el parámetro `included` contra la lista blanca del modelo.
    public function scopeIncluded(Builder $query)
    {
        $included = request('included');

        if (
            empty($this->allowIncluded) ||
            !is_string($included) ||
            $included === ''
        ) {
            return;
        }

        $relations = explode(',', $included);
        $allowIncluded = collect($this->allowIncluded);

        foreach ($relations as $key => $relationship) {
            $relationship = trim($relationship);

            if (
                $relationship === '' ||
                !$allowIncluded->contains($relationship)
            ) {
                unset($relations[$key]);
            }
        }

        $query->with($relations);
    }

    // ---------------------------------------------------------------- Filtros de listado
    public function scopeSearch(Builder $query, ?string $term)
    {
        if (empty($term)) return $query;
        $t = "%{$term}%";
        return $query->where(function (Builder $q) use ($t) {
            $q->whereHas('project1', fn (Builder $qq) => $qq->where('titulo', 'like', $t))
              ->orWhereHas('project2', fn (Builder $qq) => $qq->where('titulo', 'like', $t));
        });
    }

    public function scopeByFicha(Builder $query, $fichaId)
    {
        if (empty($fichaId) || $fichaId === 'todos') return $query;
        return $query->where(function (Builder $q) use ($fichaId) {
            $q->whereHas('project1', fn (Builder $qq) => $qq->where('id_class_group', $fichaId))
              ->orWhereHas('project2', fn (Builder $qq) => $qq->where('id_class_group', $fichaId));
        });
    }

    public function scopeByPrograma(Builder $query, ?string $programa)
    {
        if (empty($programa) || $programa === 'todos') return $query;
        return $query->where(function (Builder $q) use ($programa) {
            $q->whereHas('project1.classGroup.program', fn (Builder $qq) => $qq->where('nombre', $programa))
              ->orWhereHas('project2.classGroup.program', fn (Builder $qq) => $qq->where('nombre', $programa));
        });
    }

    // ---------------------------------------------------------------- Alcance

    // Vigencia: el par vigente corresponde al contenido actual analizado; el
    // histórico es evidencia de una versión anterior (rechazo/reenvío).
    public function scopeVigentes(Builder $query): Builder
    {
        return $query->where('vigente', true);
    }

    public function scopeHistoricos(Builder $query): Builder
    {
        return $query->where('vigente', false);
    }

    // Pares cuya contraparte desde `$proyectoId` está aprobada (regla de
    // perspectiva): sirve para las vistas de UNA propuesta.
    public function scopeContraparteAprobada(Builder $query, int $proyectoId): Builder
    {
        return $query->where(function (Builder $q) use ($proyectoId) {
            $q->where(function (Builder $x) use ($proyectoId) {
                $x->where('id_proyecto_1', $proyectoId)
                  ->whereHas('project2', fn (Builder $p) => $p->where('estado', 'aprobado'));
            })->orWhere(function (Builder $x) use ($proyectoId) {
                $x->where('id_proyecto_2', $proyectoId)
                  ->whereHas('project1', fn (Builder $p) => $p->where('estado', 'aprobado'));
            });
        });
    }

    // El motor solo compara propuestas del mismo programa (`Recomputador`). Un
    // par manual puede cruzar programas y, en ese caso, no debe revelar la
    // contraparte: quien lo consulta no puede abrir ese contenido por su
    // alcance académico. La igualdad se evalúa con los programas actuales de
    // ambas propuestas (no con una copia guardada en el par).
    public function scopeMismoPrograma(Builder $query): Builder
    {
        return $query->whereHas('project2.classGroup', function (Builder $q) {
            $q->whereIn('class_groups.id_programa', function ($sub) {
                $sub->select('cg1.id_programa')
                    ->from('class_groups as cg1')
                    ->join('projects as p1', 'p1.id_class_group', '=', 'cg1.id')
                    ->whereColumn('p1.id', 'similarities.id_proyecto_1');
            });
        });
    }

    // Listados. Regla: una propuesta pendiente NUNCA es la referencia de otra;
    // el par se muestra si AL MENOS UNO de los dos lados está aprobado. Así las
    // propuestas pendientes (A) aparecen comparadas contra su referencia (B)
    // tanto para admin como para instructor, sin exponer pares pendiente-pendiente.
    public function scopeParaUsuario(Builder $query, $user): Builder
    {
        if (!$user) return $query->whereRaw('1 = 0');

        $alMenosUnaAprobada = function (Builder $q) {
            $q->whereHas('project1', fn (Builder $p) => $p->where('estado', 'aprobado'))
              ->orWhereHas('project2', fn (Builder $p) => $p->where('estado', 'aprobado'));
        };

        if ($user->rol === 'admin') {
            return $query->where($alMenosUnaAprobada);
        }

        if ($user->rol === 'instructor') {
            // El par debe tocar una ficha que el instructor DICTA. Estar solo
            // asignado (`id_instructor_asignado`) abre la propuesta en
            // `Project::paraDetalle`, pero no habilita por sí solo listar el
            // par: es una diferencia preexistente que requiere decisión
            // funcional (ver informe del Lote 16 §3).
            $fichaIds = ClassGroup::whereHas('instructor', fn (Builder $q) => $q->where('id_usuario', $user->id))
                ->pluck('id');
            return $query->where(function (Builder $q) use ($fichaIds) {
                $q->whereHas('project1', fn (Builder $qq) => $qq->whereIn('id_class_group', $fichaIds))
                  ->orWhereHas('project2', fn (Builder $qq) => $qq->whereIn('id_class_group', $fichaIds));
            })->where($alMenosUnaAprobada)
              ->where(function (Builder $q) use ($user) {
                  // Ambas propuestas deben ser legibles por el rol (mismo
                  // programa o asignación): un par manual que cruza programas
                  // no debe listarse si su contenido no se puede abrir.
                  $q->whereHas('project1', fn (Builder $qq) => $qq->paraDetalle($user))
                    ->whereHas('project2', fn (Builder $qq) => $qq->paraDetalle($user));
              });
        }

        // Aprendiz: la contraparte (el lado ajeno) debe estar aprobada y ser del
        // mismo programa; si ambas propuestas del par son suyas, se muestra.
        // La restricción de programa aplica solo al lado ajeno: un par manual
        // cross-program no habilita a leer la contraparte (el motor nunca lo
        // generaría). Los pares del motor cumplen la condición por diseño.
        $mio = fn (Builder $qq) => $qq->where('id_creador', $user->id)
            ->orWhereHas('apprentices', fn (Builder $a) => $a->where('id_usuario', $user->id));
        $aprobado = fn (Builder $p) => $p->where('estado', 'aprobado');

        return $query->where(function (Builder $q) use ($mio, $aprobado) {
            $q->where(function (Builder $x) use ($mio, $aprobado) {
                $x->whereHas('project1', $mio)
                  ->whereHas('project2', $aprobado)
                  ->mismoPrograma();
            })->orWhere(function (Builder $x) use ($mio, $aprobado) {
                $x->whereHas('project2', $mio)
                  ->whereHas('project1', $aprobado)
                  ->mismoPrograma();
            })->orWhere(function (Builder $x) use ($mio) {
                $x->whereHas('project1', $mio)->whereHas('project2', $mio);
            });
        });
    }

    // Detalle de UNA similitud. Admin cualquiera; instructor las de sus fichas
    // cuando la contraparte también es legible (mismo programa/asignación);
    // aprendiz la regla de listado.
    public function scopeVisibleDetalle(Builder $query, $user): Builder
    {
        if (!$user) return $query->whereRaw('1 = 0');
        if ($user->rol === 'admin') return $query;

        if ($user->rol === 'instructor') {
            // Mismo alcance que el listado: fichas que el instructor DICTA.
            // La asignación (`id_instructor_asignado`) por sí sola no abre el
            // detalle de similitud (diferencia preexistente documentada).
            $fichaIds = ClassGroup::whereHas('instructor', fn (Builder $q) => $q->where('id_usuario', $user->id))
                ->pluck('id');
            return $query->where(function (Builder $q) use ($fichaIds) {
                $q->whereHas('project1', fn (Builder $qq) => $qq->whereIn('id_class_group', $fichaIds))
                  ->orWhereHas('project2', fn (Builder $qq) => $qq->whereIn('id_class_group', $fichaIds));
            })->where(function (Builder $q) use ($user) {
                // La contraparte también debe ser legible (mismo programa o
                // asignación). Así el detalle nunca expone contenido de una
                // propuesta que el instructor no puede abrir.
                $q->whereHas('project1', fn (Builder $qq) => $qq->paraDetalle($user))
                  ->whereHas('project2', fn (Builder $qq) => $qq->paraDetalle($user));
            });
        }

        // Aprendiz: par vigente con la regla de perspectiva, o evidencia
        // histórica de una propuesta propia (versiones anteriores).
        $mio = fn (Builder $qq) => $qq->where('id_creador', $user->id)
            ->orWhereHas('apprentices', fn (Builder $a) => $a->where('id_usuario', $user->id));

        return $query->where(function (Builder $q) use ($user, $mio) {
            $q->where(function (Builder $qq) use ($user) {
                $qq->paraUsuario($user);
            })->orWhere(function (Builder $qq) use ($mio) {
                // Misma regla de programa que el listado: la evidencia
                // histórica de un par cross-program no se expone a quien no
                // puede abrir la contraparte. Si ambas propuestas son suyas,
                // sigue siendo su propia evidencia.
                $qq->historicos()->where(function (Builder $x) use ($mio) {
                    $x->where(function (Builder $ambas) use ($mio) {
                        $ambas->whereHas('project1', $mio)
                               ->whereHas('project2', $mio);
                    })->orWhere(function (Builder $una) use ($mio) {
                        $una->where(function (Builder $lado) use ($mio) {
                            $lado->whereHas('project1', $mio)->mismoPrograma();
                        })->orWhere(function (Builder $lado) use ($mio) {
                            $lado->whereHas('project2', $mio)->mismoPrograma();
                        });
                    });
                });
            });
        });
    }

    // Filtra los pares que tocan uno o varios proyectos (lista separada por comas).
    public function scopeRelatedTo(Builder $query, $projectIds): Builder
    {
        $ids = collect(is_array($projectIds) ? $projectIds : explode(',', (string) $projectIds))
            ->map(fn ($id) => (int) trim((string) $id))
            ->filter()
            ->values();

        if ($ids->isEmpty()) return $query;

        return $query->where(function (Builder $q) use ($ids) {
            $q->whereIn('id_proyecto_1', $ids)->orWhereIn('id_proyecto_2', $ids);
        });
    }

    // ---------------------------------------------------------------- Relaciones
    public function project1()
    {
        return $this->belongsTo(Project::class, 'id_proyecto_1');
    }

    public function project2()
    {
        return $this->belongsTo(Project::class, 'id_proyecto_2');
    }
}
