package com.manguessr.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(

        @NotBlank(message = "L'email est obligatoire.")
        @Email(message = "Format d'email invalide.")
        String email,

        @NotBlank(message = "Le pseudo est obligatoire.")
        @Size(min = 3, max = 24, message = "Le pseudo doit faire entre 3 et 24 caracteres.")
        @Pattern(regexp = "^[\\p{Alnum}_-]+$",
                 message = "Le pseudo ne peut contenir que des lettres, chiffres, tirets et underscores.")
        String username,

        @NotBlank(message = "Le mot de passe est obligatoire.")
        @Size(min = 8, max = 128, message = "Le mot de passe doit faire au moins 8 caracteres.")
        String password
) {}
