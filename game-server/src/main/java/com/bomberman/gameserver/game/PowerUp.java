package com.bomberman.gameserver.game;

/** Un power-up tirado en el suelo, esperando a que alguien lo recoja. */
public record PowerUp(Position position, PowerUpType type) {
}
