$ErrorActionPreference = "Stop"

function Read-RequiredValue {
    param([string]$Prompt)
    while ($true) {
        $value = Read-Host $Prompt
        if (-not [string]::IsNullOrWhiteSpace($value)) {
            return $value
        }
        Write-Host "A value is required."
    }
}

function Read-RequiredSecret {
    param([string]$Prompt)
    $secureValue = Read-Host $Prompt -AsSecureString
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureValue)
    try {
        return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
    }
    finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
    }
}

function New-RandomBase64Value {
    $bytes = [byte[]]::new(32)
    $generator = [Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $generator.GetBytes($bytes)
        return [Convert]::ToBase64String($bytes)
    }
    finally {
        $generator.Dispose()
    }
}

if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
    throw "Maven is not available on PATH. Install Maven 3.9+ and retry."
}

$repositoryRoot = Split-Path -Parent $PSScriptRoot
$userServicePath = Join-Path $repositoryRoot "user-service"
$bookingServicePath = Join-Path $repositoryRoot "booking-service"
$paymentServicePath = Join-Path $repositoryRoot "payment-service"
$occupiedPorts = Get-NetTCPConnection -State Listen -LocalPort 8082,8083,8084 -ErrorAction SilentlyContinue
if ($occupiedPorts) {
    $ports = ($occupiedPorts | Select-Object -ExpandProperty LocalPort -Unique) -join ", "
    throw "Ports $ports are already in use. Stop the existing services before starting another local copy."
}
$jwtSecret = New-RandomBase64Value
$companionApiKey = New-RandomBase64Value
$geoapifyApiKey = Read-RequiredSecret "Geoapify API key (enable Geocoding and Routing APIs)"
$razorpayKeyId = Read-RequiredValue "Razorpay test-mode Key ID"
$razorpayKeySecret = Read-RequiredSecret "Razorpay test-mode Key Secret"
$razorpayWebhookSecret = Read-RequiredSecret "Razorpay webhook secret"
$databaseUsername = Read-RequiredValue "PostgreSQL username"
$databasePassword = Read-RequiredSecret "PostgreSQL password"
$mailUsername = Read-RequiredValue "SMTP username (required to send registration OTPs)"
$mailPassword = Read-RequiredSecret "SMTP password or app password"
$userDatabaseUrl = Read-Host "User-service JDBC URL [jdbc:postgresql://localhost:5432/startocare_users]"
if ([string]::IsNullOrWhiteSpace($userDatabaseUrl)) {
    $userDatabaseUrl = "jdbc:postgresql://localhost:5432/startocare_users"
}
$bookingDatabaseUrl = Read-Host "Booking-service JDBC URL [jdbc:postgresql://localhost:5432/bookingservice]"
if ([string]::IsNullOrWhiteSpace($bookingDatabaseUrl)) {
    $bookingDatabaseUrl = "jdbc:postgresql://localhost:5432/bookingservice"
}
$paymentDatabaseUrl = Read-Host "Payment-service JDBC URL [jdbc:postgresql://localhost:5432/startocare_payments]"
if ([string]::IsNullOrWhiteSpace($paymentDatabaseUrl)) {
    $paymentDatabaseUrl = "jdbc:postgresql://localhost:5432/startocare_payments"
}

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw "Docker is required to start the local Kafka broker. Install Docker Desktop and retry."
}
& docker compose -f (Join-Path $repositoryRoot "docker-compose.yml") up -d kafka
if ($LASTEXITCODE -ne 0) {
    throw "Could not start Kafka. Check Docker Desktop and port 9092, then retry."
}

$env:JWT_SECRET = $jwtSecret
$env:COMPANION_API_KEY = $companionApiKey
$env:GEOAPIFY_API_KEY = $geoapifyApiKey
$env:RAZORPAY_KEY_ID = $razorpayKeyId
$env:RAZORPAY_KEY_SECRET = $razorpayKeySecret
$env:RAZORPAY_WEBHOOK_SECRET = $razorpayWebhookSecret
$env:DB_USERNAME = $databaseUsername
$env:DB_PASSWORD = $databasePassword
$env:MAIL_USERNAME = $mailUsername
$env:MAIL_PASSWORD = $mailPassword
$env:MAIL_SMTP_AUTH = "true"
$env:MAIL_SMTP_STARTTLS_ENABLE = "true"

$env:DB_URL = $userDatabaseUrl
$userProcess = Start-Process -FilePath (Join-Path $PSHOME "powershell.exe") `
    -ArgumentList @("-NoExit", "-Command", "mvn spring-boot:run") `
    -WorkingDirectory $userServicePath `
    -PassThru

$env:DB_URL = $bookingDatabaseUrl
$bookingProcess = Start-Process -FilePath (Join-Path $PSHOME "powershell.exe") `
    -ArgumentList @("-NoExit", "-Command", "mvn spring-boot:run") `
    -WorkingDirectory $bookingServicePath `
    -PassThru

$env:DB_URL = $paymentDatabaseUrl
$paymentProcess = Start-Process -FilePath (Join-Path $PSHOME "powershell.exe") `
    -ArgumentList @("-NoExit", "-Command", "mvn spring-boot:run") `
    -WorkingDirectory $paymentServicePath `
    -PassThru

Write-Host "Started user-service terminal (PID $($userProcess.Id)) at http://localhost:8082"
Write-Host "Started booking-service terminal (PID $($bookingProcess.Id)) at http://localhost:8083"
Write-Host "Started payment-service terminal (PID $($paymentProcess.Id)) at http://localhost:8084"
Write-Host "Shared JWT, internal API, and local Kafka settings were configured for these service processes."
Write-Host "Do not close the service terminals while using the APIs."
