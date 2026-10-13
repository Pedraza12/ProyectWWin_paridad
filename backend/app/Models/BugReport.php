<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Builder;

class BugReport extends Model
{
    use HasFactory;

    // Relaciones en camelCase en el JSON (el frontend es JS).
    public static $snakeAttributes = false;

    protected $fillable = [
        'titulo', 'numero_ficha', 'motivo', 'descripcion', 'tipo', 'estado',
        'respuesta', 'fecha', 'id_usuario',
    ];

    // Normaliza cualquier fecha ISO/datetime a la columna `date`.
    public function setFechaAttribute($valor): void
    {
        $this->attributes['fecha'] = $valor ? substr((string) $valor, 0, 10) : null;
    }

    protected $allowIncluded = [
        'generalUser',
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

    public function generalUser()
    {
        return $this->belongsTo(GeneralUser::class, 'id_usuario');
    }
}
