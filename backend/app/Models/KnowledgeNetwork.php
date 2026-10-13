<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Builder;

class KnowledgeNetwork extends Model
{
    use HasFactory;

    // Relaciones en camelCase en el JSON (el frontend es JS).
    public static $snakeAttributes = false;

    protected $fillable = ['nombre'];

    // Sin relaciones incluibles: la lectura es pública y no debe exponer
    // fichas ni aprendices.
    protected $allowIncluded = [];

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

    public function trainingPrograms()
    {
        return $this->hasMany(TrainingProgram::class, 'knowledge_network_id');
    }
}
