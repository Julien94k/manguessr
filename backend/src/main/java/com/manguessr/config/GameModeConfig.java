package com.manguessr.config;

import com.manguessr.model.enums.GameMode;
import com.manguessr.model.enums.ThemeKind;
import com.manguessr.repository.MediaWorkRepository;
import com.manguessr.service.game.AnswerMatcher;
import com.manguessr.service.game.ScoreCalculator;
import com.manguessr.service.game.mode.RoundSupport;
import com.manguessr.service.game.mode.ThemeModeHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Les modes Opening et Ending partagent toute leur logique et ne different que par le type
 * de generique : plutot que deux classes jumelles, on enregistre deux instances de la meme.
 */
@Configuration
public class GameModeConfig {

    @Bean
    public ThemeModeHandler openingModeHandler(MediaWorkRepository workRepository,
                                               AnswerMatcher answerMatcher,
                                               ScoreCalculator scoreCalculator,
                                               GameProperties properties,
                                               RoundSupport support) {
        return new ThemeModeHandler(GameMode.OPENING, ThemeKind.OP,
                workRepository, answerMatcher, scoreCalculator, properties, support);
    }

    @Bean
    public ThemeModeHandler endingModeHandler(MediaWorkRepository workRepository,
                                              AnswerMatcher answerMatcher,
                                              ScoreCalculator scoreCalculator,
                                              GameProperties properties,
                                              RoundSupport support) {
        return new ThemeModeHandler(GameMode.ENDING, ThemeKind.ED,
                workRepository, answerMatcher, scoreCalculator, properties, support);
    }
}
