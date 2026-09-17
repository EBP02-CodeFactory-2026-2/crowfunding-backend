# 1. Etapa de compilación: USAR JDK 21 
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app

# Descarga dependencias buscando dentro de la carpeta backend
COPY backend/.mvn/ .mvn
COPY backend/mvnw backend/pom.xml ./
RUN chmod +x ./mvnw
RUN ./mvnw dependency:go-offline -B

# Copia el código fuente desde backend y compila
COPY backend/src ./src
RUN ./mvnw clean package -DskipTests

# 2. Etapa de ejecución: USAR JRE 21 (Para producción)
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Copia el JAR optimizado desde el builder
COPY --from=builder /app/target/*.jar app.jar

# Expone el puerto y arranca la aplicación
EXPOSE 8080
ENTRYPOINT [ "java", "-jar", "app.jar" ]
