<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Builder;

class TrainingProgram extends Model
{
    use HasFactory;

    // Relaciones en camelCase en el JSON (el frontend es JS).
    public static $snakeAttributes = false;

    protected $fillable = ['nombre', 'nivel', 'knowledge_network_id'];

    // Solo lo que el frontend necesita: las rutas públicas de catálogos no
    // deben poder cargar aprendices ni fichas (PII y códigos de unión).
    protected $allowIncluded = [
        'knowledgeNetwork',
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

    public function apprentices()
    {
        return $this->hasMany(Apprentice::class, 'id_programa');
    }

    public function classGroups()
    {
        return $this->hasMany(ClassGroup::class, 'id_programa');
    }

    public function knowledgeNetwork()
    {
        return $this->belongsTo(KnowledgeNetwork::class, 'knowledge_network_id');
    }
}
