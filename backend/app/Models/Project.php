<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Builder;
use Illuminate\Support\Facades\Log;

class Project extends Model
{
    use HasFactory;

    // Relaciones en camelCase en el JSON (el frontend es JS).
    public static $snakeAttributes = false;

    protected $fillable = [
        'titulo', 'resumen', 'palabras_clave', 'area_aplicacion',
        'objetivo_general', 'objetivos_especificos', 'estado', 'huella_envio',
        'id_creador', 'id_instructor_asignado', 'id_class_group',
    ];

    protected $casts = [
        'objetivos_especificos' => 'array',
    ];

    protected $allowIncluded = [
        'creator',
        'instructor',
        'instructor.generalUser',
        'classGroup',
        'classGroup.program',
        'apprentices',
        'apprentices.generalUser',
        'comments',
        'similaritiesAsOrigin',
        'similaritiesAsDestination',
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
                // P-04: el descarte silencioso ocultaba `included` mal escritos
                // o no autorizados. Se registra un aviso sin PII: solo el
                // nombre de relación si tiene forma de identificador.
                Log::warning('Filtro included: relación no autorizada descartada.', [
                    'modelo' => static::class,
                    'include' => preg_match('/^[A-Za-z0-9_.]{1,60}$/', $relationship) === 1
                        ? $relationship
                        : '[valor no identificador]',
                ]);
                unset($relations[$key]);
            }
        }

        // Las similitudes incluidas no deben exponer pares que el rol no puede
        // abrir (p. ej. un par manual cross-program creado por un admin). Se
        // cargan acotadas por `visibleDetalle`; el admin conserva la vista
        // completa. Sin usuario autenticado no se incluyen.
        $usuario = request()->user();
        $acotadas = [];

        if (!$usuario || $usuario->rol !== 'admin') {
            foreach (['similaritiesAsOrigin', 'similaritiesAsDestination'] as $rel) {
                $pos = array_search($rel, $relations, true);
                if ($pos === false) {
                    continue;
                }

                unset($relations[$pos]);
                if ($usuario) {
                    $acotadas[$rel] = fn ($q) => $q->visibleDetalle($usuario);
                }
            }
        }

        $query->with(array_values($relations));

        if (!empty($acotadas)) {
            $query->with($acotadas);
        }
    }

    // ---------------------------------------------------------------- Filtros de listado
    public function scopeSearch(Builder $query, ?string $term)
    {
        if (empty($term)) return $query;
        $t = "%{$term}%";
        return $query->where(function (Builder $q) use ($t) {
            $q->where('titulo', 'like', $t)->orWhereHas('creator', fn (Builder $qq) => $qq->where('nombre', 'like', $t));
        });
    }

    public function scopeByEstado(Builder $query, ?string $estado)
    {
        if (empty($estado) || $estado === 'todos') return $query;
        return $query->where('estado', $estado);
    }

    public function scopeByFicha(Builder $query, $fichaId)
    {
        if (empty($fichaId) || $fichaId === 'todos') return $query;
        return $query->where('id_class_group', $fichaId);
    }

    public function scopeByPrograma(Builder $query, ?string $programa)
    {
        if (empty($programa) || $programa === 'todos') return $query;
        return $query->whereHas('classGroup.program', fn (Builder $q) => $q->where('nombre', $programa));
    }

    // Alcance por rol para LISTADOS. Sin esto, cualquier autenticado listaba
    // TODAS las propuestas (título, resumen, objetivos).
    public function scopeParaUsuario(Builder $query, $user): Builder
    {
        if (!$user) return $query->whereRaw('1 = 0');
        if ($user->rol === 'admin') return $query;

        if ($user->rol === 'instructor') {
            $instructorId = Instructor::where('id_usuario', $user->id)->value('id');
            if (!$instructorId) return $query->whereRaw('1 = 0');
            return $query->where(function (Builder $q) use ($instructorId) {
                $q->where('id_instructor_asignado', $instructorId)
                  ->orWhereHas('classGroup', fn (Builder $qq) => $qq->where('id_instructor', $instructorId));
            });
        }

        // Aprendiz: sus propuestas (creador o equipo) + las de su ficha (aula).
        $fichaIds = Apprentice::where('id_usuario', $user->id)->pluck('id_class_group')->filter();
        return $query->where(function (Builder $q) use ($user, $fichaIds) {
            $q->where('id_creador', $user->id)
              ->orWhereHas('apprentices', fn (Builder $a) => $a->where('id_usuario', $user->id));
            if ($fichaIds->isNotEmpty()) {
                $q->orWhereIn('id_class_group', $fichaIds);
            }
        });
    }

    // Propuestas "propias" (para comentarios): el aprendiz NO ve las ajenas de
    // su ficha; el instructor sí las de sus fichas/asignadas.
    public function scopeDeAutor(Builder $query, $user): Builder
    {
        if (!$user) return $query->whereRaw('1 = 0');
        if ($user->rol === 'admin') return $query;

        if ($user->rol === 'instructor') {
            $instructorId = Instructor::where('id_usuario', $user->id)->value('id');
            if (!$instructorId) return $query->whereRaw('1 = 0');
            return $query->where(function (Builder $q) use ($instructorId) {
                $q->where('id_instructor_asignado', $instructorId)
                  ->orWhereHas('classGroup', fn (Builder $qq) => $qq->where('id_instructor', $instructorId));
            });
        }

        return $query->where(function (Builder $q) use ($user) {
            $q->where('id_creador', $user->id)
              ->orWhereHas('apprentices', fn (Builder $a) => $a->where('id_usuario', $user->id));
        });
    }

    // Detalle (show): el instructor puede leer en modo lectura las propuestas de
    // su mismo programa (así lo permite la UI); el aprendiz, las suyas y su ficha.
    public function scopeParaDetalle(Builder $query, $user): Builder
    {
        if (!$user) return $query->whereRaw('1 = 0');
        if ($user->rol === 'admin') return $query;

        if ($user->rol === 'instructor') {
            $instructorId = Instructor::where('id_usuario', $user->id)->value('id');
            $programaIds = $instructorId
                ? ClassGroup::where('id_instructor', $instructorId)->pluck('id_programa')->filter()->unique()
                : collect();
            if (!$instructorId && $programaIds->isEmpty()) return $query->whereRaw('1 = 0');
            return $query->where(function (Builder $q) use ($instructorId, $programaIds) {
                if ($instructorId) {
                    $q->where('id_instructor_asignado', $instructorId)
                      ->orWhereHas('classGroup', fn (Builder $qq) => $qq->where('id_instructor', $instructorId));
                }
                if ($programaIds->isNotEmpty()) {
                    $q->orWhereHas('classGroup', fn (Builder $qq) => $qq->whereIn('id_programa', $programaIds));
                }
            });
        }

        $fichaIds = Apprentice::where('id_usuario', $user->id)->pluck('id_class_group')->filter();
        return $query->where(function (Builder $q) use ($user, $fichaIds) {
            $q->where('id_creador', $user->id)
              ->orWhereHas('apprentices', fn (Builder $a) => $a->where('id_usuario', $user->id));
            if ($fichaIds->isNotEmpty()) {
                $q->orWhereIn('id_class_group', $fichaIds);
            }
        });
    }

    // Vista (NO gestión): propuesta propia/de la ficha o contraparte de una
    // similitud autorizada del usuario (solo lectura, aunque sea de otra ficha).
    // La gestión sigue dependiendo de `puedeEscribir`/ownership en los controllers.
    public function scopeVisiblePara(Builder $query, $user): Builder
    {
        if (!$user) return $query->whereRaw('1 = 0');
        if ($user->rol !== 'aprendiz') return $query->paraDetalle($user);

        return $query->where(function (Builder $q) use ($user) {
            $q->paraDetalle($user)
              ->orWhere(function (Builder $qq) use ($user) {
                  $qq->whereHas('similaritiesAsOrigin', fn (Builder $s) => $s->visibleDetalle($user))
                     ->orWhereHas('similaritiesAsDestination', fn (Builder $s) => $s->visibleDetalle($user));
              });
        });
    }

    // Regla Classroom: se PARTICIPA (comentar/editar/gestionar equipo) solo
    // dentro de la ficha ACTIVA de la propuesta. Fuera de la ficha (o con la
    // ficha finalizada) es SOLO LECTURA. Instructor de la ficha/asignado y admin
    // siempre pueden.
    public function puedeEscribir($user): bool
    {
        if (!$user) return false;
        if ($user->rol === 'admin') return true;

        if ($user->rol === 'instructor') {
            $instructor = Instructor::where('id_usuario', $user->id)->first();
            if (!$instructor) return false;
            return (int) $this->id_instructor_asignado === (int) $instructor->id
                || (int) optional($this->classGroup)->id_instructor === (int) $instructor->id;
        }

        $ficha = $this->classGroup;
        if (!$ficha || $ficha->estado !== 'activo') return false;

        $aprendiz = Apprentice::where('id_usuario', $user->id)->first();
        if (!$aprendiz || (int) $aprendiz->id_class_group !== (int) $ficha->id) return false;

        if ((int) $this->id_creador === (int) $user->id) return true;

        return ApprenticeProject::where('id_proyecto', $this->id)
            ->where('id_aprendiz', $aprendiz->id)
            ->exists();
    }

    // ---------------------------------------------------------------- Relaciones
    public function creator()
    {
        return $this->belongsTo(GeneralUser::class, 'id_creador');
    }

    public function instructor()
    {
        return $this->belongsTo(Instructor::class, 'id_instructor_asignado');
    }

    public function classGroup()
    {
        return $this->belongsTo(ClassGroup::class, 'id_class_group');
    }

    public function apprentices()
    {
        return $this->belongsToMany(Apprentice::class, 'apprentice_projects', 'id_proyecto', 'id_aprendiz');
    }

    public function comments()
    {
        return $this->hasMany(Comment::class, 'id_proyecto');
    }

    public function histories()
    {
        return $this->hasMany(ProjectHistory::class, 'id_proyecto')->orderByDesc('id');
    }

    public function similaritiesAsOrigin()
    {
        return $this->hasMany(Similarity::class, 'id_proyecto_1');
    }

    public function similaritiesAsDestination()
    {
        return $this->hasMany(Similarity::class, 'id_proyecto_2');
    }
}
