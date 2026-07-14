## Important

The vanilla ADVANCE_TIME and ADVANCE_WEATHER gamerules are always set to false. The mod manages time and weather progression directly.

## Features

* Change the speed of time.
* Freeze the day-night cycle.
* Control natural weather.
* Schedule rain and thunderstorms.
* Use the mod as an API.

## Gamerules

### Time scale

```mcfunction
/gamerule chronocycles:time_scale 1
```

* `0` freezes time.
* `1` uses vanilla speed.
* Higher values make time slower.

### Weather cycle mode

```mcfunction
/gamerule chronocycles:weather_cycle_mode 2
```

* `0` disables natural weather changes.
* `1` scales weather with time.
* `2` uses vanilla weather speed.
