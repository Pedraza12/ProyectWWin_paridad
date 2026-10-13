<?php

namespace App\Models;

use Illuminate\Auth\Authenticatable;
use Illuminate\Auth\Passwords\CanResetPassword;
use Illuminate\Contracts\Auth\Authenticatable as AuthenticatableContract;
use Illuminate\Contracts\Auth\CanResetPassword as CanResetPasswordContract;
use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Builder;
use Illuminate\Notifications\Notifiable;
use Laravel\Sanctum\HasApiTokens;

class GeneralUser extends Model implements AuthenticatableContract, CanResetPasswordContract
{
    use Authenticatable, CanResetPassword, HasFactory, HasApiTokens, Notifiable;

    // Relaciones en camelCase en el JSON (el frontend es JS).
    public static $snakeAttributes = false;

    protected $fillable = [
        'nombre', 'apellido', 'tipo_documento', 'numero_documento', 'correo',
        'username', 'password', 'must_change_password', 'password_temporal',
        'credenciales_enviadas_en', 'credenciales_error',
        'foto_url', 'rol', 'estado',
    ];

    // Datos sensibles que nunca salen por la API (login/me/listados). La
    // contraseña temporal se descifra solo al exportar credenciales. Los campos
    // institucionales/credenciales se ocultan por defecto y solo los revelan
    // los endpoints de administración (makeVisible).
    protected $hidden = [
        'password', 'remember_token', 'password_temporal',
        'tipo_documento', 'numero_documento', 'username',
        'must_change_password', 'credenciales_enviadas_en', 'credenciales_error',
    ];

    protected $casts = [
        'estado' => 'boolean',
        'must_change_password' => 'boolean',
        'credenciales_enviadas_en' => 'datetime',
    ];

    // El correo es el identificador de acceso (no existe columna `email`).
    public function getEmailForPasswordReset()
    {
        return $this->correo;
    }

    // Toda cuenta nace con username (seed, altas del admin, pruebas).
    protected static function booted(): void
    {
        static::creating(function (GeneralUser $user) {
            if (empty($user->username)) {
                $user->username = \App\Support\Credenciales::usernameSeguro(
                    (string) $user->nombre,
                    (string) $user->apellido
                );
            }
        });
    }

    // Las notificaciones por correo se envían al `correo` institucional.
    public function routeNotificationForMail()
    {
        return [$this->correo => trim(($this->nombre ?? '') . ' ' . ($this->apellido ?? ''))];
    }

    public function sendPasswordResetNotification($token)
    {
        $this->notify(new \App\Notifications\ResetPasswordNotification($token));
    }

    protected $allowIncluded = [
        'apprentice',
        'instructor',
        'admin',
        'projects',
        'notifications',
        'comments',
        'bugReports',
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
            $q->where('nombre', 'like', $t)
              ->orWhere('apellido', 'like', $t)
              ->orWhere('username', 'like', $t)
              ->orWhere('numero_documento', 'like', $t)
              ->orWhere('correo', 'like', $t);
        });
    }

    public function scopeByRol(Builder $query, ?string $rol)
    {
        if (empty($rol) || $rol === 'todos') return $query;
        return $query->where('rol', $rol);
    }

    public function scopeByEstado(Builder $query, ?string $estado)
    {
        if (empty($estado) || $estado === 'todos') return $query;
        $val = $estado === 'activo' ? 1 : ($estado === 'suspendido' ? 0 : $estado);
        return $query->where('estado', $val);
    }

    public function scopeByFicha(Builder $query, $fichaId)
    {
        if (empty($fichaId) || $fichaId === 'todos') return $query;
        if ($fichaId === 'sin') {
            return $query->whereDoesntHave('apprentice', fn (Builder $q) => $q->whereNotNull('id_class_group'));
        }
        return $query->whereHas('apprentice', fn (Builder $q) => $q->where('id_class_group', $fichaId));
    }

    public function scopeByPrograma(Builder $query, ?string $programa)
    {
        if (empty($programa) || $programa === 'todos') return $query;
        // El OR va agrupado: si no, rompería el AND con los demás filtros.
        return $query->where(function (Builder $q) use ($programa) {
            $q->whereHas('apprentice.program', fn (Builder $qq) => $qq->where('nombre', $programa))
              ->orWhereHas('apprentice.classGroup.program', fn (Builder $qq) => $qq->where('nombre', $programa));
        });
    }

    // ---------------------------------------------------------------- Relaciones
    public function apprentice()
    {
        return $this->hasOne(Apprentice::class, 'id_usuario');
    }

    public function instructor()
    {
        return $this->hasOne(Instructor::class, 'id_usuario');
    }

    public function admin()
    {
        return $this->hasOne(Admin::class, 'id_usuario');
    }

    public function projects()
    {
        return $this->hasMany(Project::class, 'id_creador');
    }

    public function notifications()
    {
        return $this->hasMany(Notification::class, 'id_usuario');
    }

    public function comments()
    {
        return $this->hasMany(Comment::class, 'id_usuario');
    }

    public function bugReports()
    {
        return $this->hasMany(BugReport::class, 'id_usuario');
    }
}
