#Requires -Version 5.1
<#
.SYNOPSIS
  Fetch production logs from Render (default service: karuta-tracker-api).

.DESCRIPTION
  Reads credentials (RENDER_API_KEY / RENDER_OWNER_ID / RENDER_SERVICE_ID) from the
  "Render API" section of CLAUDE.local.md, then calls the Render Logs API
  (GET https://api.render.com/v1/logs) and prints the logs.

  - The script itself contains no secrets, so it is safe to commit.
  - api.render.com is plain public HTTPS, unrelated to the Postgres-host NAT64/IPv6 issue.
  - ASCII-only on purpose: avoids Windows PowerShell 5.1 CP932 mis-decoding of UTF-8 .ps1 files.

.PARAMETER Hours
  How many hours back to fetch (default 6). Ignored when -Start / -End are given.

.PARAMETER Start
  ISO8601 start time, e.g. 2026-06-28T10:00:00Z. Takes precedence over -Hours.

.PARAMETER End
  ISO8601 end time. Defaults to now.

.PARAMETER Text
  Substring filter on the log body (repeatable, OR).

.PARAMETER Level
  Filter by log level (e.g. error, warning, info).

.PARAMETER Type
  Filter by log type (app / request / build).

.PARAMETER StatusCode
  Filter by HTTP status code (e.g. 500, 404). For request logs.

.PARAMETER Method
  Filter by HTTP method (e.g. GET, POST).

.PARAMETER Path
  Filter by request path (e.g. /api/matches).

.PARAMETER Limit
  Items per page (1-100, default 100).

.PARAMETER MaxPages
  Max number of pages to page through (default 1). Increase to go further back.

.PARAMETER Json
  Output raw JSON instead of formatted lines.

.PARAMETER CredFile
  Explicit path to the credentials file (default: walk up from the script to find CLAUDE.local.md).

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File scripts/render-logs/Get-RenderLogs.ps1 -Hours 6 -Level error

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File scripts/render-logs/Get-RenderLogs.ps1 -Text "NullPointerException" -Hours 24 -MaxPages 5

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File scripts/render-logs/Get-RenderLogs.ps1 -StatusCode 500 -Type request -Hours 24
#>
[CmdletBinding()]
param(
  [double]$Hours = 6,
  [string]$Start,
  [string]$End,
  [string[]]$Text,
  [string[]]$Level,
  [ValidateSet('app', 'request', 'build')][string[]]$Type,
  [string[]]$StatusCode,
  [string[]]$Method,
  [string[]]$Path,
  [ValidateRange(1, 100)][int]$Limit = 100,
  [int]$MaxPages = 1,
  [switch]$Json,
  [string]$CredFile
)

$ErrorActionPreference = 'Stop'

# --- Locate the credentials file (CLAUDE.local.md) ---
function Resolve-CredFile {
  param([string]$Explicit)
  if ($Explicit) {
    if (-not (Test-Path -LiteralPath $Explicit)) { throw "CredFile not found: $Explicit" }
    return $Explicit
  }
  $dir = $PSScriptRoot
  for ($i = 0; $i -lt 6 -and $dir; $i++) {
    $candidate = Join-Path $dir 'CLAUDE.local.md'
    if (Test-Path -LiteralPath $candidate) { return $candidate }
    $dir = Split-Path $dir -Parent
  }
  throw "CLAUDE.local.md not found. Pass -CredFile explicitly."
}

$credPath = Resolve-CredFile -Explicit $CredFile
# PowerShell 5.1 defaults to CP932; read the markdown as UTF-8.
$content = Get-Content -LiteralPath $credPath -Raw -Encoding UTF8

function Get-CredValue {
  param([string]$Key)
  $pattern = '(?m)^\s*' + [regex]::Escape($Key) + '\s*=\s*(.+?)\s*$'
  $m = [regex]::Match($content, $pattern)
  if (-not $m.Success) { return $null }
  return $m.Groups[1].Value.Trim()
}

$apiKey = Get-CredValue 'RENDER_API_KEY'
$ownerId = Get-CredValue 'RENDER_OWNER_ID'
$serviceId = Get-CredValue 'RENDER_SERVICE_ID'

if (-not $apiKey -or $apiKey -like '*<*') {
  throw "RENDER_API_KEY is not set. Check the 'Render API' section of $credPath"
}
if (-not $serviceId -or $serviceId -like '*<*') { throw "RENDER_SERVICE_ID is not set ($credPath)." }
if (-not $ownerId -or $ownerId -like '*<*') { throw "RENDER_OWNER_ID is not set ($credPath)." }

# --- Time range (UTC, ISO8601) ---
if (-not $End) { $End = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ") }
if (-not $Start) { $Start = (Get-Date).ToUniversalTime().AddHours(-$Hours).ToString("yyyy-MM-ddTHH:mm:ssZ") }

# --- Query builder ---
function Add-Param {
  param([System.Collections.Generic.List[string]]$List, [string]$Name, $Values)
  foreach ($v in $Values) {
    if ($null -ne $v -and "$v" -ne '') {
      $List.Add("$Name=" + [uri]::EscapeDataString("$v"))
    }
  }
}

$headers = @{ Authorization = "Bearer $apiKey"; Accept = 'application/json' }
$base = 'https://api.render.com/v1/logs'

$page = 0
$all = New-Object System.Collections.Generic.List[object]
$curStart = $Start
$curEnd = $End
$resp = $null

do {
  $page++
  $q = New-Object System.Collections.Generic.List[string]
  Add-Param $q 'ownerId' $ownerId
  Add-Param $q 'resource' $serviceId
  Add-Param $q 'startTime' $curStart
  Add-Param $q 'endTime' $curEnd
  Add-Param $q 'limit' $Limit
  Add-Param $q 'text' $Text
  Add-Param $q 'level' $Level
  Add-Param $q 'type' $Type
  Add-Param $q 'statusCode' $StatusCode
  Add-Param $q 'method' $Method
  Add-Param $q 'path' $Path
  $url = $base + '?' + ($q -join '&')

  try {
    $resp = Invoke-RestMethod -Uri $url -Headers $headers -Method Get
  }
  catch {
    $msg = $_.Exception.Message
    if ($_.Exception.Response) {
      try {
        $sr = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
        $body = $sr.ReadToEnd()
        if ($body) { $msg += " | body: $body" }
      }
      catch {}
    }
    throw "Render Logs API call failed: $msg"
  }

  if ($resp.logs) { foreach ($l in $resp.logs) { $all.Add($l) } }

  $more = [bool]$resp.hasMore -and ($page -lt $MaxPages)
  if ($more) {
    $curStart = $resp.nextStartTime
    $curEnd = $resp.nextEndTime
  }
} while ($more)

# --- Output ---
if ($Json) {
  $all | ConvertTo-Json -Depth 8
  return
}

if ($all.Count -eq 0) {
  Write-Host "(no logs: $Start - $End / service=$serviceId)"
  return
}

foreach ($l in $all) {
  $ts = $l.timestamp
  $msg =
  if ($null -ne $l.message) { $l.message }
  elseif ($null -ne $l.text) { $l.text }
  else { ($l | ConvertTo-Json -Compress -Depth 6) }

  $lvl = ''
  if ($l.labels) {
    $lv = ($l.labels | Where-Object { $_.name -eq 'level' } | Select-Object -First 1).value
    if ($lv) { $lvl = "[$lv] " }
  }
  Write-Output ("{0} {1}{2}" -f $ts, $lvl, $msg)
}

Write-Host ""
Write-Host ("--- {0} lines / {1} - {2} ---" -f $all.Count, $Start, $End)
if ([bool]$resp.hasMore) {
  Write-Host "(hasMore=true: increase -MaxPages to fetch more)"
}
