package com.bcp.workshop.auth;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

@QuarkusTest
class AuthResourceTest {
    @Test
    void logsInWithWorkshopUser() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"Workshop123!\",\"client\":\"angular-workshop\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(200)
                .body("user.username", is("student"))
                .body("token", notNullValue());
    }

    @Test
    void logsInWithSecondWorkshopUser() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"trainer\",\"password\":\"Trainer123!\",\"client\":\"angular-workshop\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(200)
                .body("user.username", is("trainer"))
                .body("token", notNullValue());
    }

    @Test
    void rejectsOrdinaryWrongPasswordWithoutToken() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"wrong-password\",\"client\":\"angular-workshop\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(401)
                .body("token", nullValue());
    }

    @Test
    void rejectsInjectionStyleUsernameWithoutToken() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"' OR '1'='1\",\"password\":\"Workshop123!\",\"client\":\"angular-workshop\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(401)
                .body("token", nullValue());
    }

    @Test
    void rejectsInjectionStylePasswordWithoutToken() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"' OR '1'='1\",\"client\":\"angular-workshop\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(401)
                .body("token", nullValue());
    }
}
