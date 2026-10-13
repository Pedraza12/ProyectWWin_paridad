<?php

namespace App\Http\Controllers\Api;

use App\Models\TrainingProgram;
use App\Http\Controllers\Controller;
use Illuminate\Http\Request;

class TrainingProgramController extends Controller
{
    public function index()
    {
        return TrainingProgram::included()->get();
    }

    public function store(Request $request)
    {
        $request->validate([
            'nombre' => 'required|max:255',
            'nivel' => 'required|max:255',
            'knowledge_network_id' => 'required|exists:knowledge_networks,id',
        ]);

        $item = TrainingProgram::create($request->only(['nombre', 'nivel', 'knowledge_network_id']));
        return response()->json($item, 201);
    }

    public function show($id)
    {
        return TrainingProgram::included()->findOrFail($id);
    }

    public function update(Request $request, TrainingProgram $training_program)
    {
        $request->validate([
            'nombre' => 'required|max:255',
            'nivel' => 'required|max:255',
            'knowledge_network_id' => 'required|exists:knowledge_networks,id',
        ]);

        $training_program->update($request->only(['nombre', 'nivel', 'knowledge_network_id']));
        return $training_program;
    }

    public function destroy(TrainingProgram $training_program)
    {
        // Admin sin restricciones: borra el programa y sus dependientes en
        // cascada (fichas con propuestas/aprendices).
        $impacto = [
            'fichas' => \App\Models\ClassGroup::where('id_programa', $training_program->id)->count(),
            'aprendices' => \App\Models\Apprentice::where('id_programa', $training_program->id)->count(),
        ];

        \App\Support\BorradoCascada::programa($training_program);

        \App\Support\Auditoria::registrar('eliminar_programa', 'training_programs', $training_program->id, [
            'nombre' => $training_program->nombre,
            'impacto' => $impacto,
        ]);

        return $training_program;
    }
}
