# Créditos y licencias del frontend

| Recurso | Origen | Licencia |
|---|---|---|
| Motor de juego **Phaser 3.90** (`lib/phaser.min.js`) | https://phaser.io (copiado de cdnjs) | MIT |
| Sprites: jugadores, enemigos, bombas, llamas, muros, bloques, power-ups, corazones | **Hechos por nosotros** en código (`src/textures.js`), como pixel art de 16x16 escrito con letras | Propios |
| Efectos de sonido | **Sintetizados** en tiempo real con la Web Audio API (`src/sound.js`) | Propios |
| Tipografía | Fuentes del sistema (Trebuchet MS / Verdana) | Sistema |

No usamos ningún sprite, sonido ni logotipo de los juegos originales de Bomberman (son propiedad de Konami/Hudson Soft). La mecánica de juego (bombas en cruz, bloques destructibles…) no está protegida por derechos de autor. Lo que sí está protegido son el arte y la marca concretos.

Si en el futuro se quieren usar gráficos externos, buenas opciones libres son:
- **Kenney.nl**: licencia CC0 (dominio público), no exige atribución.
- **OpenGameArt.org**: revisar la licencia de cada recurso (CC0, CC-BY, CC-BY-SA...). Las CC-BY exigen citar al autor en este archivo.
