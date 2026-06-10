param(
    [string]$InstallDir = "$PSScriptRoot\..\runtime\dify",
    [string]$DifyRepo = "https://github.com/langgenius/dify.git",
    [string]$Checkout = "",
    [int]$WebPort = 3001
)

$ErrorActionPreference = "Stop"

$installPath = Resolve-Path -LiteralPath (New-Item -ItemType Directory -Force -Path $InstallDir)
$repoPath = Join-Path $installPath "dify"

if (-not (Test-Path (Join-Path $repoPath ".git"))) {
    git clone $DifyRepo $repoPath
}

Push-Location $repoPath
try {
    if ($Checkout) {
        git fetch --tags
        git checkout $Checkout
    }

    $dockerDir = Join-Path $repoPath "docker"
    if (-not (Test-Path $dockerDir)) {
        throw "Dify docker directory not found: $dockerDir"
    }

    Push-Location $dockerDir
    try {
        if (-not (Test-Path ".env")) {
            Copy-Item ".env.example" ".env"
        }

        $envText = Get-Content -Raw -Encoding UTF8 ".env"
        $envText = $envText -replace "(?m)^EXPOSE_WEB_PORT=.*$", "EXPOSE_WEB_PORT=$WebPort"
        if ($envText -notmatch "(?m)^EXPOSE_WEB_PORT=") {
            $envText += "`nEXPOSE_WEB_PORT=$WebPort`n"
        }
        Set-Content -Encoding UTF8 ".env" $envText

        docker compose up -d
    } finally {
        Pop-Location
    }
} finally {
    Pop-Location
}

Write-Host "Dify official service should be available at http://localhost:$WebPort"
