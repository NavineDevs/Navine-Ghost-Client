$ErrorActionPreference = 'Stop'

$instancePath = 'C:\Users\hitbo\curseforge\minecraft\Instances\navine-client (1)'
$instanceGuid = '6d57153a-eb41-4320-9bc2-5f176bcead55'
$java = 'C:\Program Files\BellSoft\LibericaJDK-25\bin\java.exe'
$injector = 'C:\Users\hitbo\Downloads\output\GhostInjector.jar'

if (-not (Test-Path $injector)) {
    throw "GhostInjector.jar not found at $injector"
}

Write-Host 'Injecting ghost client...'
& $java -jar $injector inject --instance $instancePath
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$agent = Get-CimInstance Win32_Process -Filter "Name='Curse.Agent.Host.exe'" -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $agent) {
    throw 'CurseForge agent is not running. Open CurseForge first.'
}

$port = 54461
if ($agent.CommandLine -match '--port=(\d+)') {
    $port = [int]$Matches[1]
}

$payloadMarker = Join-Path $env:APPDATA '.minecraft\libraries\com\mojang\datafixerupper\8.0.16\.navine-ghost-agent'
$payload = if (Test-Path $payloadMarker) { Get-Content $payloadMarker -Raw } else { '' }
$payload = $payload.Trim()
$extraArgs = "-javaagent:`"$injector`""
if ($payload) {
    $extraArgs += " -Dfabric.addMods=`"$payload`""
}

$launchPayload = @{
    args = @(
        @{
            MinecraftInstanceGuid = $instanceGuid
            ResolutionWidth = 1024
            ResolutionHeight = 768
            LauncherVisibility = 'Close'
            LauncherType = 'Classic'
            AdditionalJavaArguments = $extraArgs
        }
    )
    type = 'method'
    name = 'minecraftTaskLaunchInstance'
} | ConvertTo-Json -Depth 6 -Compress

Add-Type -AssemblyName System.Net.WebSockets
Add-Type -AssemblyName System.Threading

$uri = [Uri]"ws://127.0.0.1:$port/CurseForge"
$ws = [System.Net.WebSockets.ClientWebSocket]::new()
$cts = [System.Threading.CancellationTokenSource]::new()
$ws.ConnectAsync($uri, $cts.Token).GetAwaiter().GetResult()

$bytes = [System.Text.Encoding]::UTF8.GetBytes($launchPayload)
$segment = [ArraySegment[byte]]::new($bytes)
$ws.SendAsync($segment, [System.Net.WebSockets.WebSocketMessageType]::Text, $true, $cts.Token).GetAwaiter().GetResult()
Start-Sleep -Seconds 1
$ws.CloseAsync([System.Net.WebSockets.WebSocketCloseStatus]::NormalClosure, 'done', $cts.Token).GetAwaiter().GetResult() | Out-Null

Write-Host "Launch requested for navine-client on CurseForge port $port."
Write-Host 'Minecraft should start shortly. Press ` in-game for the ghost GUI.'
