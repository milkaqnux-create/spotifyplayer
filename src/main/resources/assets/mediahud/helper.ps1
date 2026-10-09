param([string]$CoverDir, [string]$CmdFile, [int]$ParentPid)
$ErrorActionPreference = 'SilentlyContinue'
[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false)
Add-Type -AssemblyName System.Runtime.WindowsRuntime
Add-Type -AssemblyName System.Drawing
$null = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType = WindowsRuntime]
$null = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties, Windows.Media.Control, ContentType = WindowsRuntime]
$null = [Windows.Storage.Streams.IRandomAccessStreamWithContentType, Windows.Storage.Streams, ContentType = WindowsRuntime]
$null = [Windows.Storage.Streams.IInputStream, Windows.Storage.Streams, ContentType = WindowsRuntime]
$null = [Windows.Storage.Streams.DataReader, Windows.Storage.Streams, ContentType = WindowsRuntime]

$asTask = [System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
    $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'
} | Select-Object -First 1

function Await($op, [Type]$type) {
    $task = $asTask.MakeGenericMethod($type).Invoke($null, @($op))
    $task.Wait(5000) | Out-Null
    return $task.Result
}

$script:coverCounter = 0

function Read-Bytes($ras) {
    try {
        $ms = New-Object System.IO.MemoryStream
        $st = [System.IO.WindowsRuntimeStreamExtensions]::AsStreamForRead([Windows.Storage.Streams.IInputStream]$ras)
        $st.CopyTo($ms)
        if ($ms.Length -gt 0) { return ,$ms.ToArray() }
    } catch { [Console]::Error.WriteLine('cover stream error: ' + $_.Exception.Message) }
    try {
        $size = [uint32]$ras.Size
        $reader = New-Object Windows.Storage.Streams.DataReader($ras.GetInputStreamAt(0))
        $null = Await ($reader.LoadAsync($size)) ([uint32])
        $bytes = New-Object byte[] $size
        $reader.ReadBytes($bytes)
        return ,$bytes
    } catch { [Console]::Error.WriteLine('cover reader error: ' + $_.Exception.Message) }
    return $null
}

function Save-Cover($props) {
    try {
        if ($null -eq $props.Thumbnail) { [Console]::Error.WriteLine('cover: brak miniatury'); return '' }
        $ras = Await ($props.Thumbnail.OpenReadAsync()) ([Windows.Storage.Streams.IRandomAccessStreamWithContentType])
        if ($null -eq $ras) { [Console]::Error.WriteLine('cover: stream null'); return '' }
        $bytes = Read-Bytes $ras
        if ($null -eq $bytes -or $bytes.Length -eq 0) { [Console]::Error.WriteLine('cover: puste dane'); return '' }
        $ms = New-Object System.IO.MemoryStream(,$bytes)
        $src = [System.Drawing.Image]::FromStream($ms)
        $bmp = New-Object System.Drawing.Bitmap 128, 128
        $g = [System.Drawing.Graphics]::FromImage($bmp)
        $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
        $g.DrawImage($src, 0, 0, 128, 128)
        $g.Dispose()
        $src.Dispose()
        $script:coverCounter++
        $path = Join-Path $CoverDir ('cover_' + $script:coverCounter + '.png')
        $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
        $bmp.Dispose()
        [Console]::Error.WriteLine('cover ok: ' + $path)
        return $path
    } catch {
        [Console]::Error.WriteLine('cover error: ' + $_.Exception.Message)
        return ''
    }
}

function New-Manager {
    try {
        return (Await ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]))
    } catch {
        [Console]::Error.WriteLine('manager error: ' + $_.Exception.Message)
        return $null
    }
}

$mgr = New-Manager
[Console]::Error.WriteLine('helper started, manager=' + ($null -ne $mgr) + ', apartment=' + [System.Threading.Thread]::CurrentThread.GetApartmentState())

function Get-Session {
    if ($null -eq $mgr) { return $null }
    $sp = $mgr.GetSessions() | Where-Object { $_.SourceAppUserModelId -like '*Spotify*' } | Select-Object -First 1
    if ($sp) { return $sp }
    return $mgr.GetCurrentSession()
}

function Run-Commands {
    if (-not (Test-Path $CmdFile)) { return }
    $proc = $CmdFile + '.proc'
    try { Move-Item $CmdFile $proc -Force } catch { return }
    $lines = [System.IO.File]::ReadAllLines($proc)
    Remove-Item $proc -Force
    $s = Get-Session
    if (-not $s) { return }
    foreach ($l in $lines) {
        switch ($l.Trim()) {
            'next'   { $null = $s.TrySkipNextAsync() }
            'prev'   { $null = $s.TrySkipPreviousAsync() }
            'toggle' { $null = $s.TryTogglePlayPauseAsync() }
        }
    }
}

$lastKey = ''
$lastJson = ''
$cover = ''
$tries = 0
$tick = 0

while ($true) {
    Run-Commands

    # odswiezaj menedzera co ~2 s, zeby nie trzymac nieaktualnych danych
    if (($tick % 20) -eq 0 -and $tick -gt 0) {
        $m = New-Manager
        if ($m) { $mgr = $m }
    }

    if (($tick % 4) -eq 0) {
        if ($ParentPid -gt 0 -and -not (Get-Process -Id $ParentPid -ErrorAction SilentlyContinue)) { break }
        try {
            $s = Get-Session
            $out = @{ none = $true }
            if ($s) {
                $props = Await ($s.TryGetMediaPropertiesAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])
                if ($props -and ($props.Title -or $props.Artist)) {
                    $key = $props.Title + '|' + $props.Artist
                    if ($key -ne $lastKey) { $lastKey = $key; $cover = ''; $tries = 0 }
                    if ($cover -eq '' -and $tries -lt 15) { $tries++; $cover = Save-Cover $props }
                    $status = $s.GetPlaybackInfo().PlaybackStatus.ToString()
                    $pos = 0.0
                    $dur = 0.0
                    $tl = $s.GetTimelineProperties()
                    if ($tl) {
                        $dur = ($tl.EndTime - $tl.StartTime).TotalSeconds
                        $pos = $tl.Position.TotalSeconds
                        if ($status -eq 'Playing') {
                            $el = ([DateTimeOffset]::UtcNow - $tl.LastUpdatedTime).TotalSeconds
                            if ($el -gt 0 -and $el -lt 600) { $pos += $el }
                        }
                        if ($dur -gt 0 -and $pos -gt $dur) { $pos = $dur }
                    }
                    $out = @{
                        title   = [string]$props.Title
                        artist  = [string]$props.Artist
                        playing = ($status -eq 'Playing')
                        cover   = [string]$cover
                        pos     = [long]($pos * 1000)
                        dur     = [long]($dur * 1000)
                    }
                }
            }
            $json = $out | ConvertTo-Json -Compress
            if ($json -ne $lastJson) {
                $lastJson = $json
                [Console]::Out.WriteLine($json)
                [Console]::Out.Flush()
            }
        } catch {
            [Console]::Error.WriteLine('loop error: ' + $_.Exception.Message)
        }
    }
    $tick++
    Start-Sleep -Milliseconds 100
}
