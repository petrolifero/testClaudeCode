# syntax=docker/dockerfile:1

# ---------- build ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /projeto

# O pom vem sozinho primeiro para que a camada de dependencias seja
# reaproveitada enquanto so o codigo muda.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
# Testes NAO rodam aqui de proposito: a suite sobe um PostgreSQL via
# Testcontainers, o que exige um daemon Docker que o build da imagem nao tem.
# Rode "mvn verify" fora da imagem (e a CI faz exatamente isso).
RUN mvn -B -q -DskipTests package

# ---------- execucao ----------
FROM eclipse-temurin:21-jre-noble AS runtime

# A aplicacao chama o BINARIO do tesseract (ver MotorDeOcrTesseract), por isso
# ele e o idioma portugues entram na imagem final.
RUN apt-get update \
    && apt-get install -y --no-install-recommends tesseract-ocr tesseract-ocr-por \
    && rm -rf /var/lib/apt/lists/*

ENV GUPY_OCR_DATAPATH=/usr/share/tesseract-ocr/5/tessdata
ENV GUPY_OCR_IDIOMAS=por+eng
# Falha no build, e nao em producao na primeira pagina digitalizada, se a
# distribuicao mudar o caminho do tessdata.
RUN test -d "$GUPY_OCR_DATAPATH" && tesseract --list-langs

RUN useradd --system --create-home --shell /usr/sbin/nologin gupy
USER gupy
WORKDIR /app
COPY --from=build /projeto/target/gupy-clone-*.jar /app/gupy-clone.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/gupy-clone.jar"]
