# نکات سرعت‌بخش این Dockerfile:
#  1. لایه dependency از COPY کد جداست — تا وقتی pom.xml عوض نشده،
#     Docker همون لایه cache شده رو استفاده می‌کنه.
#  2. -T 1C روی mvn — build با چند thread موازی برابر با تعداد CPU.
#  3. -o (offline) روی build نهایی — از رفتن به Maven Central روی هر بیلد
#     جلوگیری می‌کنه چون قبلاً go-offline اجرا شده.
#
# نکته: BuildKit cache mount (--mount=type=cache) عمداً استفاده نمی‌شود چون
# Docker daemon لیارا BuildKit فعال ندارد.

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# لایه ۱: dependency resolution — فقط با تغییر pom.xml re-run می‌شه
COPY pom.xml .
RUN mvn -B -T 1C dependency:go-offline

# لایه ۲: build — با هر تغییر src re-run می‌شه ولی dependencyها از layer قبل می‌آن
COPY src ./src
RUN mvn -B -T 1C -o clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseSerialGC -Xss256k -XX:TieredStopAtLevel=1"
EXPOSE 8080
ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar app.jar"]
