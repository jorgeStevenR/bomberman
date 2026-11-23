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
        $ws.ConnectAsync([Uri]"$wsBase/ws/game?room=$room", $cts.Token).Wait()
        $join = [Text.Encoding]::UTF8.GetBytes('{"type":"JOIN","name":"demo"}')
        $ws.SendAsync([ArraySegment[byte]]$join, 'Text', $true, $cts.Token).Wait()

        $buffer = New-Object byte[] 65536
        $serverId = $null
        while (-not $serverId) {
            $res = $ws.ReceiveAsync([ArraySegment[byte]]$buffer, $cts.Token).Result
            $msg = [Text.Encoding]::UTF8.GetString($buffer, 0, $res.Count) | ConvertFrom-Json
            if ($msg.type -eq 'WELCOME') { $serverId = $msg.serverId }
        }
        "sala {0,-6} → {1}" -f $room, $serverId
        $ws.CloseAsync('NormalClosure', 'demo', [Threading.CancellationToken]::None).Wait()
    } catch {
        "sala {0,-6} → ERROR: {1}" -f $room, $_.Exception.InnerException.Message
    } finally {
        $ws.Dispose()
    }
}

Write-Host "`nLas salas repetidas (ALFA, BETA) caen siempre en la misma réplica." -ForegroundColor Green
Write-Host "Mira el reparto en Grafana: http://localhost:3001 (dashboard 'Bomberman — Logs y balanceo')`n"
