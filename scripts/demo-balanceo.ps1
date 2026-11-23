<#
  Demostración del balanceo de carga (para la presentación).

  1) API REST (least_conn): 10 peticiones a /api/health → se ve qué instancia
     (api-1 / api-2) contesta cada una y que la base de datos responde.
  2) Game-servers (hash por sala): abre un WebSocket a 6 salas distintas y muestra
     qué réplica (game-1 / game-2 / game-3) atiende cada una. Repetir la misma sala
     siempre cae en la misma réplica.

  Uso (con "docker compose up -d" en marcha):
      powershell -ExecutionPolicy Bypass -File scripts\demo-balanceo.ps1
#>
param([string]$BaseUrl = "http://localhost:8080")

Write-Host "`n=== API REST: algoritmo least_conn entre api-1 y api-2 ===" -ForegroundColor Yellow
for ($i = 1; $i -le 10; $i++) {
    try {
        $r = Invoke-WebRequest -UseBasicParsing "$BaseUrl/api/health"
        $body = $r.Content | ConvertFrom-Json
        "{0,2}. instancia={1,-6} base de datos={2}  (hora BD: {3})" -f $i, $body.instance, $body.database, $body.dbTime
    } catch {
        "{0,2}. ERROR: {1}" -f $i, $_.Exception.Message
    }
}

Write-Host "`n=== Game-servers: hash consistente por código de sala ===" -ForegroundColor Yellow
$wsBase = $BaseUrl -replace '^http', 'ws'
$rooms = @('ALFA', 'BETA', 'GAMMA', 'DELTA', 'OMEGA', 'SIGMA', 'ALFA', 'BETA')

foreach ($room in $rooms) {
    $ws = New-Object System.Net.WebSockets.ClientWebSocket
    $cts = New-Object System.Threading.CancellationTokenSource 5000
    try {
        if (-not $ws.ConnectAsync([Uri]"$wsBase/ws/game?room=$room", $cts.Token).Wait(5000)) { throw "sin respuesta al conectar" }
        $join = [Text.Encoding]::UTF8.GetBytes('{"type":"JOIN","name":"demo"}')
        $segment = New-Object 'ArraySegment[byte]' (, $join)
        $ws.SendAsync($segment, [Net.WebSockets.WebSocketMessageType]::Text, $true, $cts.Token).Wait(5000) | Out-Null

        # El servidor contesta con WELCOME, que incluye "server" = réplica que atiende la sala
        $buffer = New-Object byte[] 65536
        $serverId = $null
        $deadline = (Get-Date).AddSeconds(5)
        while (-not $serverId -and (Get-Date) -lt $deadline -and $ws.State -eq 'Open') {
            $task = $ws.ReceiveAsync((New-Object 'ArraySegment[byte]' (, $buffer)), $cts.Token)
            if (-not $task.Wait(5000)) { break }
            $msg = [Text.Encoding]::UTF8.GetString($buffer, 0, $task.Result.Count) | ConvertFrom-Json
            if ($msg.type -eq 'WELCOME') { $serverId = $msg.server }
            if ($msg.type -eq 'ERROR') { throw "$($msg.code): $($msg.message)" }
        }
        if (-not $serverId) { throw "el servidor no respondió" }
        "sala {0,-6} → {1}" -f $room, $serverId
        try { $ws.CloseAsync('NormalClosure', 'demo', [Threading.CancellationToken]::None).Wait(2000) | Out-Null } catch { }
    } catch {
        $err = if ($_.Exception.InnerException) { $_.Exception.InnerException.Message } else { $_.Exception.Message }
        "sala {0,-6} → ERROR: {1}" -f $room, $err
    } finally {
        $ws.Dispose()
    }
}

Write-Host "`nLas salas repetidas (ALFA, BETA) caen siempre en la misma réplica." -ForegroundColor Green
Write-Host "Mira el reparto en Grafana: http://localhost:3001 (dashboard 'Bomberman — Logs y balanceo')`n"
