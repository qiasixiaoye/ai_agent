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
    if ($Checkout) {
        git clone $DifyRepo $repoPath
    } else {
        git clone --depth 1 $DifyRepo $repoPath
    }
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to clone official Dify repository. Check GitHub network access and retry."
    }
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
        $envText = $envText -replace "(?m)^EXPOSE_NGINX_PORT=.*$", "EXPOSE_NGINX_PORT=$WebPort"
        if ($envText -notmatch "(?m)^EXPOSE_NGINX_PORT=") {
            $envText += "`nEXPOSE_NGINX_PORT=$WebPort`n"
        }
        $envText = $envText -replace "(?m)^EXPOSE_NGINX_SSL_PORT=.*$", "EXPOSE_NGINX_SSL_PORT=3443"
        $envText = $envText -replace "(?m)^CONSOLE_API_URL=.*$", "CONSOLE_API_URL=http://localhost:$WebPort"
        $envText = $envText -replace "(?m)^CONSOLE_WEB_URL=.*$", "CONSOLE_WEB_URL=http://localhost:$WebPort"
        $envText = $envText -replace "(?m)^SERVICE_API_URL=.*$", "SERVICE_API_URL=http://localhost:$WebPort"
        $envText = $envText -replace "(?m)^APP_WEB_URL=.*$", "APP_WEB_URL=http://localhost:$WebPort"
        Set-Content -Encoding UTF8 ".env" $envText

        docker compose up -d
    } finally {
        Pop-Location
    }
} finally {
    Pop-Location
}

Write-Host "Dify official service should be available at http://localhost:$WebPort"
