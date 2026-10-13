<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Builder;

class Comment extends Model
{
    use HasFactory;

    // Relaciones en camelCase en el JSON (el frontend es JS).
    public static $snakeAttributes = false;

    protected $fillable = ['texto', 'id_proyecto', 'id_usuario', 'respuesta_a'];

    protected $allowIncluded = [
        'project',
        'user',
        'parent',
        'replies',
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

    public function project()
    {
        return $this->belongsTo(Project::class, 'id_proyecto');
    }

    // Solo se ven las observaciones de propuestas propias (o de las fichas del
    // instructor). Un compañero de ficha NO ve las observaciones ajenas.
    public function scopeParaUsuario(Builder $query, $user): Builder
    {
        if (!$user) return $query->whereRaw('1 = 0');
        if ($user->rol === 'admin') return $query;

        return $query->whereHas('project', fn (Builder $q) => $q->deAutor($user));
    }

    public function user()
    {
        return $this->belongsTo(GeneralUser::class, 'id_usuario');
    }

    public function parent()
    {
        return $this->belongsTo(Comment::class, 'respuesta_a');
    }

    public function replies()
    {
        return $this->hasMany(Comment::class, 'respuesta_a');
    }
}
