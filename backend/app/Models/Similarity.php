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

    // Listados. Regla: una propuesta pendiente NUNCA figura como coincidencia
    // de otra; solo se muestra si la contraparte está aprobada.
    public function scopeParaUsuario(Builder $query, $user): Builder
    {
        if (!$user) return $query->whereRaw('1 = 0');

        $ambasAprobadas = function (Builder $q) {
            $q->whereHas('project1', fn (Builder $p) => $p->where('estado', 'aprobado'))
              ->whereHas('project2', fn (Builder $p) => $p->where('estado', 'aprobado'));
        };

        if ($user->rol === 'admin') {
            return $query->where($ambasAprobadas);
        }

        if ($user->rol === 'instructor') {
            $fichaIds = ClassGroup::whereHas('instructor', fn (Builder $q) => $q->where('id_usuario', $user->id))
                ->pluck('id');
            return $query->where(function (Builder $q) use ($fichaIds) {
                $q->whereHas('project1', fn (Builder $qq) => $qq->whereIn('id_class_group', $fichaIds))
                  ->orWhereHas('project2', fn (Builder $qq) => $qq->whereIn('id_class_group', $fichaIds));
            })->where($ambasAprobadas);
        }

        // Aprendiz: la contraparte (el lado ajeno) debe estar aprobada; si ambas
        // propuestas del par son suyas, se muestra.
        $mio = fn (Builder $qq) => $qq->where('id_creador', $user->id)
            ->orWhereHas('apprentices', fn (Builder $a) => $a->where('id_usuario', $user->id));
        $aprobado = fn (Builder $p) => $p->where('estado', 'aprobado');

        return $query->where(function (Builder $q) use ($mio, $aprobado) {
            $q->where(function (Builder $x) use ($mio, $aprobado) {
                $x->whereHas('project1', $mio)->whereHas('project2', $aprobado);
            })->orWhere(function (Builder $x) use ($mio, $aprobado) {
                $x->whereHas('project2', $mio)->whereHas('project1', $aprobado);
            })->orWhere(function (Builder $x) use ($mio) {
                $x->whereHas('project1', $mio)->whereHas('project2', $mio);
            });
        });
    }

    // Detalle de UNA similitud. Admin cualquiera; instructor las de sus fichas
    // (para poder abrirlas desde la revisión); aprendiz la regla de listado.
    public function scopeVisibleDetalle(Builder $query, $user): Builder
    {
        if (!$user) return $query->whereRaw('1 = 0');
        if ($user->rol === 'admin') return $query;

        if ($user->rol === 'instructor') {
            $fichaIds = ClassGroup::whereHas('instructor', fn (Builder $q) => $q->where('id_usuario', $user->id))
                ->pluck('id');
            return $query->where(function (Builder $q) use ($fichaIds) {
                $q->whereHas('project1', fn (Builder $qq) => $qq->whereIn('id_class_group', $fichaIds))
                  ->orWhereHas('project2', fn (Builder $qq) => $qq->whereIn('id_class_group', $fichaIds));
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
                $qq->historicos()->where(function (Builder $x) use ($mio) {
                    $x->whereHas('project1', $mio)->orWhereHas('project2', $mio);
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
