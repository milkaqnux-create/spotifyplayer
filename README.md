# Media HUD

Fabric mod for **Minecraft 1.21.4** (client-side, **Windows**) that shows the song currently playing in Spotify as an on-screen player.

Mod do Minecrafta **1.21.4** (tylko klient, **Windows**), który pokazuje na ekranie aktualnie grany utwór ze Spotify.

## Funkcje / Features

- Okładka, tytuł, wykonawca / cover, title, artist
- Przyciski: poprzedni, pauza/wznów, następny / previous, pause/resume, next
- Przesuwanie panelu w czacie (przeciągnij lewym) / drag the panel while chat is open
- Prawy przycisk na panelu w czacie → menu:
  - skala i przezroczystość / scale and opacity
  - rogi okrągłe lub ostre / rounded or sharp corners
  - okładka: automatyczna, wyłączona lub własny obraz z dysku / cover: auto, off or your own image
  - tekst piosenki (synchronizowany) włącz/wyłącz / synced lyrics on/off
- Klawisze do zmiany utworu i pauzy (Opcje → Sterowanie → Media HUD)
  - domyślnie `[` poprzedni, `\` pauza/wznów, `]` następny

## Instalacja / Install

1. Zainstaluj [Fabric Loader](https://fabricmc.net/use/) dla 1.21.4 oraz [Fabric API](https://modrinth.com/mod/fabric-api).
2. Pobierz `mediahud-*.jar` z zakładki **Releases** (albo z **Actions → ostatni build → Artifacts**).
3. Wrzuć jar do folderu `mods`.
4. Włącz muzykę w **aplikacji Spotify** na komputerze.

## Własna okładka / Custom cover

W Eksploratorze: prawy przycisk na pliku → „Kopiuj jako ścieżkę".
W grze: otwórz czat, prawy przycisk na panelu → „Wklej ścieżkę obrazu".

## Jak to działa / How it works

Mod uruchamia lokalnie krótki skrypt PowerShell, który czyta dane z systemowego odtwarzacza Windows (SMTC): tytuł, wykonawcę, okładkę i pozycję utworu. Żadne konto ani klucz API nie są potrzebne.

Teksty piosenek są pobierane z [LRCLIB](https://lrclib.net) **tylko po włączeniu opcji tekstu** (wysyłana jest nazwa wykonawcy, tytuł i długość utworu).

Ustawienia: `config/mediahud.json`.

## Budowanie / Build

Wymagane: Java 21 i Gradle 8.11+.

```
gradle build
```

Gotowy plik: `build/libs/mediahud-1.0.0.jar`.
Na GitHubie build robi się sam (workflow `build`), a po wypchnięciu tagu `v*` jar trafia do Releases.

## Ograniczenia / Limitations

- Tylko Windows (SMTC). / Windows only.
- Tylko Minecraft 1.21.4. / 1.21.4 only.
- Teksty piosenek nie istnieją dla każdego utworu. / Lyrics are not available for every song.

Projekt nie jest powiązany ze Spotify ani Mojang. / Not affiliated with Spotify or Mojang.

## Licencja / License

MIT
