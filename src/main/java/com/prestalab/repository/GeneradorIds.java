package com.prestalab.repository;

import org.bson.Document;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.mapping.PersistentPropertyAccessor;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.mapping.MongoPersistentEntity;
import org.springframework.data.mongodb.core.mapping.MongoPersistentProperty;
import org.springframework.data.mongodb.core.mapping.event.BeforeConvertCallback;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import static org.springframework.data.mongodb.core.query.Criteria.where;

/**
 * Asigna ids numéricos (Long) a los documentos nuevos, como hacía BIGSERIAL en PostgreSQL. Así la API sigue
 * exponiendo ids 1, 2, 3… y ni el frontend ni la colección de Postman cambian.
 *
 * <p>Cada colección tiene su contador en la colección {@value #CONTADORES}; el incremento es atómico
 * ($inc con findAndModify), así que dos inserciones simultáneas nunca reciben el mismo id.
 */
@Component
public class GeneradorIds implements BeforeConvertCallback<Object> {

    static final String CONTADORES = "contadores";

    private final ObjectProvider<MongoOperations> mongo;

    /** ObjectProvider: MongoTemplate busca este callback al crearse, así que se resuelve al primer uso. */
    public GeneradorIds(ObjectProvider<MongoOperations> mongo) {
        this.mongo = mongo;
    }

    @Override
    public Object onBeforeConvert(Object entidad, String coleccion) {
        // Solo las entidades del dominio: los documentos BSON sueltos (por ejemplo, los candados) no llevan id numérico.
        if (!entidad.getClass().isAnnotationPresent(org.springframework.data.mongodb.core.mapping.Document.class)) {
            return entidad;
        }
        MongoOperations operaciones = mongo.getObject();
        MongoPersistentEntity<?> tipo = operaciones.getConverter().getMappingContext()
                .getPersistentEntity(entidad.getClass());
        if (tipo == null || !tipo.hasIdProperty()) {
            return entidad;
        }
        MongoPersistentProperty id = tipo.getRequiredIdProperty();
        PersistentPropertyAccessor<Object> acceso = tipo.getPropertyAccessor(entidad);
        if (acceso.getProperty(id) == null && Long.class.equals(id.getType())) {
            acceso.setProperty(id, siguiente(operaciones, coleccion));
        }
        return acceso.getBean();
    }

    private static long siguiente(MongoOperations operaciones, String coleccion) {
        Document contador = operaciones.findAndModify(
                Query.query(where("_id").is(coleccion)),
                new Update().inc("valor", 1L),
                FindAndModifyOptions.options().upsert(true).returnNew(true),
                Document.class, CONTADORES);
        return ((Number) contador.get("valor")).longValue();
    }
}
