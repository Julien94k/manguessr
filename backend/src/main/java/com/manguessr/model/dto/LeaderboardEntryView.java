package com.manguessr.model.dto;

/**
 * Une ligne du classement.
 *
 * @param rank     rang ; deux scores egaux partagent le meme rang
 * @param username pseudo public
 * @param score    score cumule sur la periode
 * @param games    parties quotidiennes comptees
 */
public record LeaderboardEntryView(int rank, String username, long score, long games) {}
