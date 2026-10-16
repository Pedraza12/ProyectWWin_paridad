<?php

use Illuminate\Support\Facades\Route;

/*
|--------------------------------------------------------------------------
| Web Routes
|--------------------------------------------------------------------------
|
| Here is where you can register web routes for your application. These
| routes are loaded by the RouteServiceProvider and all of them will
| be assigned to the "web" middleware group. Make something great!
|
*/

Route::get('/', function () {
    return view('welcome');
});

// Healthcheck del despliegue en Railway (configurado en railway.json).
// Responde 200 cuando la aplicación arranca correctamente.
Route::get('/up', function () {
    return response()->json(['status' => 'ok']);
});
