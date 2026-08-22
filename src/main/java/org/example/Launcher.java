package org.example;

import javafx.application.Application;

/**
 * Klasa startowa aplikacji.
 * <p>
 * Celowo <b>nie</b> dziedziczy po {@link Application}: gdy klasa główna rozszerza {@code Application},
 * launcher JVM wymaga modułów JavaFX na {@code --module-path} i kończy się błędem
 * „JavaFX runtime components are missing”. Pośrednia klasa startowa pozwala uruchomić aplikację
 * ze zwykłego classpath — w każdym zadaniu Gradle ({@code run}, {@code runNsga3}, …) oraz z IDE.
 */
public final class Launcher {

    private Launcher() {
    }

    public static void main(String[] args) {
        Application.launch(World.class, args);
    }
}
