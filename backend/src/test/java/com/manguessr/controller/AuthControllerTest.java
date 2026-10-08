package com.manguessr.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verrouille le contrat d'authentification : codes HTTP, forme des erreurs et regles d'unicite.
 *
 * Ces cas ont tous ete valides manuellement contre un vrai Postgres ; ce test les rejoue
 * automatiquement sur H2 pour eviter toute regression.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
// PER_CLASS : le compte de reference est cree une seule fois pour toute la classe,
// car la base H2 est partagee entre les methodes de test.
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String registeredToken;

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    @BeforeAll
    void registerReferenceAccount() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "email", "Joueur@Exemple.COM",
                                "username", "joueur",
                                "password", "motdepasse1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("joueur"))
                .andExpect(jsonPath("$.role").value("ROLE_USER"))
                .andReturn();

        registeredToken = objectMapper
                .readTree(result.getResponse().getContentAsString())
                .get("token").asText();

        assertThat(registeredToken).isNotBlank();
    }

    @Test
    void inscription_renvoie_un_token_utilisable_immediatement() throws Exception {
        // Pas de verification email : le token signe a l'inscription doit deja ouvrir /me.
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + registeredToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("joueur"))
                // L'email est normalise en minuscules au stockage.
                .andExpect(jsonPath("$.email").value("joueur@exemple.com"));
    }

    @Test
    void connexion_accepte_une_casse_d_email_differente() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "JOUEUR@exemple.com", "password", "motdepasse1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void connexion_refuse_un_mauvais_mot_de_passe_sans_reveler_l_existence_du_compte() throws Exception {
        String messageCompteConnu = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "joueur@exemple.com", "password", "mauvais"))))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        String messageCompteInconnu = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "fantome@exemple.com", "password", "motdepasse1"))))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat(messageCompteConnu).isEqualTo(messageCompteInconnu);
    }

    @Test
    void inscription_refuse_un_email_deja_pris() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "email", "joueur@exemple.com",
                                "username", "pseudo_libre",
                                "password", "motdepasse1"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Cet email est déjà utilisé."));
    }

    @Test
    void inscription_refuse_un_pseudo_deja_pris_meme_avec_une_autre_casse() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "email", "libre@exemple.com",
                                "username", "JOUEUR",
                                "password", "motdepasse1"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Ce pseudo est déjà pris."));
    }

    @Test
    void inscription_valide_le_format_du_pseudo_et_du_mot_de_passe() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "email", "libre@exemple.com",
                                "username", "pseudo invalide!",
                                "password", "motdepasse1"))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "email", "libre@exemple.com",
                                "username", "correct",
                                "password", "court"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void les_endpoints_proteges_exigent_un_token_valide() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer pas.un.jwt"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Regression : une route inexistante mais couverte par une regle publique doit repondre
     * 404, pas 500. Le filet @ExceptionHandler(Exception.class) du GlobalExceptionHandler
     * avalait NoResourceFoundException, qui implemente ErrorResponse sans etendre
     * ErrorResponseException.
     *
     * On vise /api/media/** parce que cette regle est un motif large : une route inexistante
     * y est bien atteinte par le dispatcher, la ou un chemin non declare tomberait en 401.
     */
    @Test
    void une_route_publique_inexistante_repond_404_et_non_500() throws Exception {
        mockMvc.perform(get("/api/media/route-inexistante"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not Found"));
    }

    @Test
    void une_methode_non_supportee_repond_405() throws Exception {
        mockMvc.perform(get("/api/auth/login"))
                .andExpect(status().isMethodNotAllowed());
    }
}
