<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Builder;

class ClassGroup extends Model
{
    use HasFactory;

    // Relaciones en camelCase en el JSON (el frontend es JS).
    public static $snakeAttributes = false;

    protected $fillable = ['codigo', 'numero', 'nombre', 'estado', 'id_programa', 'id_instructor'];

    protected $allowIncluded = [
        'program',
        'instructor',
        'instructor.generalUser',
        'apprentices',
        'apprentices.generalUser',
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

    // Código de unión generado por el servidor (formato xxx-xxxx en minúsculas).
    public static function codigoDisponible(): string
    {
        $letras = 'abcdefghijklmnopqrstuvwxyz';
        do {
            $codigo = '';
            for ($i = 0; $i < 7; $i++) {
                if ($i === 3) $codigo .= '-';
                $codigo .= $letras[random_int(0, 25)];
            }
        } while (static::where('codigo', $codigo)->exists());

        return $codigo;
    }

    public function program()
    {
        return $this->belongsTo(TrainingProgram::class, 'id_programa');
    }

    public function instructor()
    {
        return $this->belongsTo(Instructor::class, 'id_instructor');
    }

    public function apprentices()
    {
        return $this->hasMany(Apprentice::class, 'id_class_group');
    }
}
