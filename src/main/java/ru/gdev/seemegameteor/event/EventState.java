package ru.gdev.seemegameteor.event;

public enum EventState {
    IDLE,
    SPAWNED,
    WAITING_ACTIVATION,
    ACTIVATED,
    METEOR_FALLING,
    CRATER_READY,
    ANCHOR_SPAWNED,
    LOOT_BURST,
    GLOW_BURST,
    BEACON_FINISH,
    ENDED
}