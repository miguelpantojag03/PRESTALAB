package com.prestalab.repository;

import com.prestalab.domain.enums.EstadoEquipo;
import com.prestalab.domain.enums.EstadoPrestamo;
import com.prestalab.domain.model.Equipo;
import com.prestalab.domain.model.Prestamo;
import org.bson.Document;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.function.Supplier;

import static org.springframework.data.mongodb.core.query.Criteria.where;

/**
 * Lo que en PostgreSQL hacían los triggers de V3 y el candado pg_advisory_xact_lock, ahora con operaciones
 * atómicas de MongoDB sobre un solo documento:
 *
 * <ul>
 *   <li>{@link #guardarSiEstado}: reemplaza el documento solo si sigue en el estado de origen. Si otra operación
 *       lo cambió entretanto (por ejemplo, aprobar y rechazar a la vez), no escribe nada y devuelve false. Es el
 *       equivalente de tg_prestamos_transicion.</li>
 *   <li>{@link #conCandado}: ejecuta una acción con un candado exclusivo (un documento con _id único en la
 *       colección {@value #CANDADOS}). Las peticiones simultáneas del mismo estudiante esperan su turno, así que
 *       RN01 cuenta bien aunque lleguen seis a la vez. Es el equivalente de tg_prestamos_rn01.</li>
 * </ul>
 */
@Component
public class OperacionesAtomicas {

    static final String CANDADOS = "candados";
    /** Cuánto espera una petición por el candado antes de rendirse. */
    private static final Duration ESPERA_MAXIMA = Duration.ofSeconds(10);
    /** Un candado más viejo que esto quedó de un proceso que murió sin liberarlo. */
    private static final Duration CANDADO_ABANDONADO = Duration.ofSeconds(30);
    private static final long PAUSA_MS = 15;

    private final MongoOperations mongo;

    public OperacionesAtomicas(MongoOperations mongo) {
        this.mongo = mongo;
    }

    /** Guarda el préstamo solo si su estado en la base de datos todavía es uno de {@code estadosOrigen}. */
    public boolean guardarSiEstado(Prestamo prestamo, EstadoPrestamo... estadosOrigen) {
        return reemplazarSi(prestamo, prestamo.getId(), Arrays.stream(estadosOrigen).map(Enum::name).toArray());
    }

    /** Guarda el equipo solo si su estado en la base de datos todavía es uno de {@code estadosOrigen}. */
    public boolean guardarSiEstado(Equipo equipo, EstadoEquipo... estadosOrigen) {
        return reemplazarSi(equipo, equipo.getId(), Arrays.stream(estadosOrigen).map(Enum::name).toArray());
    }

    /**
     * Guarda el equipo que se va a prestar solo si en la base de datos sigue DISPONIBLE y activo. Si entretanto lo
     * prestaron, lo enviaron a mantenimiento o lo desactivaron, no escribe nada y devuelve false.
     */
    public boolean guardarSiDisponible(Equipo equipo) {
        Query condicion = Query.query(where("_id").is(equipo.getId())
                .and("estado").is(EstadoEquipo.DISPONIBLE.name()).and("activo").is(true));
        return mongo.replace(condicion, equipo).getMatchedCount() == 1;
    }

    private boolean reemplazarSi(Object entidad, Long id, Object[] estados) {
        Query condicion = Query.query(where("_id").is(id).and("estado").in(estados));
        return mongo.replace(condicion, entidad).getMatchedCount() == 1;
    }

    /**
     * Ejecuta {@code accion} mientras tiene el candado {@code clave}. Si no lo consigue en
     * {@link #ESPERA_MAXIMA}, ejecuta {@code alAgotarLaEspera} y lanza lo que esta devuelva.
     */
    public <T> T conCandado(String clave, Supplier<? extends RuntimeException> alAgotarLaEspera, Supplier<T> accion) {
        tomar(clave, alAgotarLaEspera);
        try {
            return accion.get();
        } finally {
            mongo.remove(Query.query(where("_id").is(clave)), CANDADOS);
        }
    }

    private void tomar(String clave, Supplier<? extends RuntimeException> alAgotarLaEspera) {
        Instant limite = Instant.now().plus(ESPERA_MAXIMA);
        while (true) {
            try {
                mongo.insert(new Document("_id", clave).append("tomado", new Date()), CANDADOS);
                return;
            } catch (DuplicateKeyException ocupado) {
                Date abandonadoAntesDe = Date.from(Instant.now().minus(CANDADO_ABANDONADO));
                mongo.remove(Query.query(where("_id").is(clave).and("tomado").lt(abandonadoAntesDe)), CANDADOS);
                if (Instant.now().isAfter(limite)) {
                    throw alAgotarLaEspera.get();
                }
                esperar();
            }
        }
    }

    private static void esperar() {
        try {
            Thread.sleep(PAUSA_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Se interrumpió la espera del candado", e);
        }
    }
}
