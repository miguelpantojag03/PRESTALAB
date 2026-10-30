-- PrestaLab: reglas que PostgreSQL hace cumplir aunque lleguen peticiones simultáneas.
-- Igual que ux_prestamos_equipo_en_curso (V1), protegen lo que el servicio verifica antes de guardar:
-- si dos transacciones pasan la verificación al mismo tiempo, la base de datos detiene a la segunda.
-- El nombre de la regla va al inicio del mensaje para que el servicio sepa cuál se incumplió.

-- RN01: máximo 2 préstamos PENDIENTE o APROBADO por usuario.
-- El candado por usuario hace que la segunda solicitud simultánea espere a la primera y luego cuente bien.
CREATE FUNCTION fn_prestamos_rn01() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    IF NEW.estado IN ('PENDIENTE', 'APROBADO') THEN
        PERFORM pg_advisory_xact_lock(NEW.usuario_id);
        IF (SELECT COUNT(*)
              FROM prestamos
             WHERE usuario_id = NEW.usuario_id
               AND estado IN ('PENDIENTE', 'APROBADO')) >= 2 THEN
            RAISE EXCEPTION 'ck_prestamos_rn01: el usuario % ya tiene 2 préstamos activos', NEW.usuario_id
                USING ERRCODE = 'check_violation';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER tg_prestamos_rn01
    BEFORE INSERT ON prestamos
    FOR EACH ROW EXECUTE FUNCTION fn_prestamos_rn01();

-- D5: solo las transiciones del diagrama de estados.
-- Si dos operaciones cambian el mismo préstamo a la vez, la segunda espera el bloqueo de la fila y aquí ve
-- el estado que dejó la primera, así que falla en lugar de sobrescribirlo.
CREATE FUNCTION fn_prestamos_transicion() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    IF NOT ((OLD.estado = 'PENDIENTE' AND NEW.estado IN ('APROBADO', 'RECHAZADO'))
         OR (OLD.estado = 'APROBADO' AND NEW.estado IN ('DEVUELTO', 'VENCIDO'))
         OR (OLD.estado = 'VENCIDO' AND NEW.estado = 'DEVUELTO')) THEN
        RAISE EXCEPTION 'ck_prestamos_transicion: el préstamo % no puede pasar de % a %',
            OLD.id, OLD.estado, NEW.estado
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER tg_prestamos_transicion
    BEFORE UPDATE OF estado ON prestamos
    FOR EACH ROW EXECUTE FUNCTION fn_prestamos_transicion();
