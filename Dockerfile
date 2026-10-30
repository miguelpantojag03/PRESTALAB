# Imagen de prestalab-app (D8). Requiere haber ejecutado antes: mvn package
FROM eclipse-temurin:21-jre

# Usuario sin privilegios: el contenedor nunca corre como root.
RUN groupadd --system prestalab && useradd --system --gid prestalab --no-create-home prestalab

COPY target/prestalab.jar /app/prestalab.jar

USER prestalab

EXPOSE 8080

ENTRYPOINT ["java","-jar","/app/prestalab.jar"]
