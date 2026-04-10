param(
    [string]$OutputPath = "docs/presentations/GrayFile-Gateway-Presentation.pptx"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Add-Textbox {
    param(
        $Slide,
        [double]$Left,
        [double]$Top,
        [double]$Width,
        [double]$Height,
        [string]$Text,
        [int]$FontSize = 18,
        [string]$FontName = "Aptos",
        [int]$Rgb = 0x1F2937,
        [switch]$Bold,
        [int]$Align = 1
    )

    $shape = $Slide.Shapes.AddTextbox(1, $Left, $Top, $Width, $Height)
    $textRange = $shape.TextFrame.TextRange
    $textRange.Text = $Text
    $textRange.Font.Name = $FontName
    $textRange.Font.Size = $FontSize
    $textRange.Font.Color.RGB = $Rgb
    $textRange.ParagraphFormat.Alignment = $Align
    if ($Bold) {
        $textRange.Font.Bold = -1
    }
    $shape.TextFrame.WordWrap = -1
    return $shape
}

function Add-Rect {
    param(
        $Slide,
        [double]$Left,
        [double]$Top,
        [double]$Width,
        [double]$Height,
        [string]$Text,
        [int]$FillRgb,
        [int]$LineRgb = 0xD1D5DB,
        [int]$FontRgb = 0x111827,
        [int]$FontSize = 18,
        [switch]$Bold,
        [int]$Adjustments = 0
    )

    $shape = $Slide.Shapes.AddShape(1, $Left, $Top, $Width, $Height)
    if ($Adjustments -gt 0) {
        for ($i = 1; $i -le $Adjustments; $i++) {
            $shape.Adjustments.Item($i) = 0.12
        }
    }
    $shape.Fill.ForeColor.RGB = $FillRgb
    $shape.Line.ForeColor.RGB = $LineRgb
    $shape.Line.Weight = 1.5
    $textRange = $shape.TextFrame.TextRange
    $textRange.Text = $Text
    $textRange.Font.Name = "Aptos"
    $textRange.Font.Size = $FontSize
    $textRange.Font.Color.RGB = $FontRgb
    if ($Bold) {
        $textRange.Font.Bold = -1
    }
    $textRange.ParagraphFormat.Alignment = 2
    $shape.TextFrame.VerticalAnchor = 3
    $shape.TextFrame.WordWrap = -1
    return $shape
}

function Add-Title {
    param($Slide, [string]$Title, [string]$Subtitle = "")
    Add-Textbox -Slide $Slide -Left 30 -Top 18 -Width 860 -Height 42 -Text $Title -FontSize 26 -Bold -Rgb 0x0F172A | Out-Null
    if ($Subtitle) {
        Add-Textbox -Slide $Slide -Left 32 -Top 58 -Width 860 -Height 22 -Text $Subtitle -FontSize 11 -Rgb 0x475569 | Out-Null
    }
    $line = $Slide.Shapes.AddShape(1, 30, 86, 870, 2)
    $line.Fill.ForeColor.RGB = 0xD97706
    $line.Line.ForeColor.RGB = 0xD97706
}

function Add-Bullets {
    param(
        $Slide,
        [double]$Left,
        [double]$Top,
        [double]$Width,
        [double]$Height,
        [string[]]$Items,
        [int]$FontSize = 19
    )

    $shape = $Slide.Shapes.AddTextbox(1, $Left, $Top, $Width, $Height)
    $shape.TextFrame.WordWrap = -1
    $shape.TextFrame.AutoSize = 0
    $shape.TextFrame.TextRange.Text = ($Items -join "`r")

    for ($i = 1; $i -le $Items.Count; $i++) {
        $paragraph = $shape.TextFrame.TextRange.Paragraphs($i)
        $paragraph.Font.Name = "Aptos"
        $paragraph.Font.Size = $FontSize
        $paragraph.Font.Color.RGB = 0x1F2937
        $paragraph.ParagraphFormat.Bullet.Visible = -1
        $paragraph.ParagraphFormat.Bullet.Character = 8226
        $paragraph.ParagraphFormat.SpaceAfter = 6
    }
    return $shape
}

function Add-Arrow {
    param(
        $Slide,
        [double]$X1,
        [double]$Y1,
        [double]$X2,
        [double]$Y2,
        [int]$Rgb = 0x64748B,
        [double]$Weight = 2
    )
    $line = $Slide.Shapes.AddLine($X1, $Y1, $X2, $Y2)
    $line.Line.ForeColor.RGB = $Rgb
    $line.Line.Weight = $Weight
    $line.Line.EndArrowheadStyle = 3
    return $line
}

function Add-SlideNumber {
    param($Slide, [int]$Number)
    Add-Textbox -Slide $Slide -Left 860 -Top 500 -Width 40 -Height 18 -Text "$Number" -FontSize 10 -Rgb 0x64748B -Align 3 | Out-Null
}

function Ensure-Folder {
    param([string]$FilePath)
    $folder = Split-Path -Path $FilePath -Parent
    if ($folder -and -not (Test-Path -LiteralPath $folder)) {
        New-Item -ItemType Directory -Path $folder -Force | Out-Null
    }
}

$absoluteOutputPath = if ([System.IO.Path]::IsPathRooted($OutputPath)) {
    $OutputPath
} else {
    Join-Path -Path (Get-Location) -ChildPath $OutputPath
}

Ensure-Folder -FilePath $absoluteOutputPath

$powerPoint = New-Object -ComObject PowerPoint.Application
$powerPoint.Visible = -1
$presentation = $powerPoint.Presentations.Add()
$presentation.PageSetup.SlideSize = 1

$themeBg = 0xFFF8EE
$accent = 0xD97706
$accentDark = 0x92400E
$blue = 0x0EA5E9
$teal = 0x14B8A6
$green = 0x22C55E
$slate = 0xE2E8F0
$softBlue = 0xE0F2FE
$softGreen = 0xDCFCE7
$softAmber = 0xFEF3C7
$softRose = 0xFFE4E6

try {
    $slideIndex = 0

    $slideIndex++
    $slide = $presentation.Slides.Add($slideIndex, 12)
    $slide.Background.Fill.ForeColor.RGB = $themeBg
    Add-Textbox -Slide $slide -Left 42 -Top 80 -Width 820 -Height 60 -Text "GrayFile Gateway`r`nFonctionnement, composants et interactions" -FontSize 28 -Bold -Rgb 0x0F172A | Out-Null
    Add-Textbox -Slide $slide -Left 45 -Top 164 -Width 760 -Height 48 -Text "Présentation générée à partir du code et de la configuration du dépôt. Progression: vue synthétique d'abord, puis approfondissement composant par composant." -FontSize 16 -Rgb 0x334155 | Out-Null
    Add-Rect -Slide $slide -Left 45 -Top 260 -Width 210 -Height 78 -Text "Envoy`r`nEdge, sécurité, throttling" -FillRgb $softAmber -LineRgb $accent -FontSize 19 -Bold | Out-Null
    Add-Rect -Slide $slide -Left 340 -Top 260 -Width 210 -Height 78 -Text "Gateway Quarkus`r`nRoutage métier et metering" -FillRgb $softBlue -LineRgb $blue -FontSize 19 -Bold | Out-Null
    Add-Rect -Slide $slide -Left 635 -Top 260 -Width 210 -Height 78 -Text "PostgreSQL + Observabilité`r`nTraçabilité et pilotage" -FillRgb $softGreen -LineRgb $green -FontSize 18 -Bold | Out-Null
    Add-Arrow -Slide $slide -X1 255 -Y1 299 -X2 340 -Y2 299 -Rgb $accentDark | Out-Null
    Add-Arrow -Slide $slide -X1 550 -Y1 299 -X2 635 -Y2 299 -Rgb $accentDark | Out-Null
    Add-Textbox -Slide $slide -Left 45 -Top 420 -Width 720 -Height 28 -Text "Cas d'usage couvert: proxy OpenAI-compatible avec capture d'usage, clôture de fenêtres de facturation et audit des changements." -FontSize 15 -Rgb 0x475569 | Out-Null
    Add-SlideNumber -Slide $slide -Number $slideIndex

    $slideIndex++
    $slide = $presentation.Slides.Add($slideIndex, 12)
    $slide.Background.Fill.ForeColor.RGB = 0xFFFFFF
    Add-Title -Slide $slide -Title "1. Vue Synthétique" -Subtitle "Résumé exécutif du rôle de la gateway dans la plateforme"
    Add-Bullets -Slide $slide -Left 44 -Top 112 -Width 818 -Height 320 -Items @(
        "GrayFile se place entre les clients et les backends LLM compatibles OpenAI pour centraliser contrôle, metering et auditabilité.",
        "Envoy absorbe les responsabilités edge: séparation des routes, authentification management, rate limiting, retries et circuit breaking.",
        "La gateway Quarkus gère les responsabilités métier: validation du scope client, sélection du backend, capture du champ usage et persistance des événements.",
        "PostgreSQL est la source de vérité pour les usages, les fenêtres de facturation, les modèles, les routes et le journal d'audit.",
        "Prometheus et Grafana rendent visibles la latence, les erreurs edge/applicatives, le volume de tokens et les clôtures de fenêtres.",
        "La conception privilégie la correction métier et la traçabilité avant l'optimisation de débit."
    ) | Out-Null
    Add-SlideNumber -Slide $slide -Number $slideIndex

    $slideIndex++
    $slide = $presentation.Slides.Add($slideIndex, 12)
    $slide.Background.Fill.ForeColor.RGB = 0xFFFFFF
    Add-Title -Slide $slide -Title "2. Architecture Globale" -Subtitle "Principaux blocs et liens entre eux"
    $clients = Add-Rect -Slide $slide -Left 38 -Top 165 -Width 115 -Height 64 -Text "Clients`r`napplications" -FillRgb 0xF8FAFC -LineRgb 0x94A3B8 -FontSize 18 -Bold
    $envoyPublic = Add-Rect -Slide $slide -Left 200 -Top 115 -Width 160 -Height 70 -Text "Envoy public :11000`r`n/llm/v1/*" -FillRgb $softAmber -LineRgb $accent -FontSize 18 -Bold
    $envoyMgmt = Add-Rect -Slide $slide -Left 200 -Top 220 -Width 160 -Height 70 -Text "Envoy management :11001`r`n/management/v1/*" -FillRgb $softRose -LineRgb 0xE11D48 -FontSize 17 -Bold
    $gateway = Add-Rect -Slide $slide -Left 418 -Top 165 -Width 175 -Height 88 -Text "GrayFile Gateway`r`nQuarkus" -FillRgb $softBlue -LineRgb $blue -FontSize 22 -Bold
    $backend = Add-Rect -Slide $slide -Left 653 -Top 90 -Width 190 -Height 74 -Text "Backend LLM`r`nvLLM / NIM / API compatible" -FillRgb 0xDBEAFE -LineRgb 0x2563EB -FontSize 17 -Bold
    $postgres = Add-Rect -Slide $slide -Left 653 -Top 205 -Width 190 -Height 74 -Text "PostgreSQL`r`nusage, billing, audit, routing" -FillRgb $softGreen -LineRgb $green -FontSize 17 -Bold
    $obs = Add-Rect -Slide $slide -Left 653 -Top 320 -Width 190 -Height 74 -Text "Prometheus + Grafana`r`nmetrics, dashboards, alerting" -FillRgb 0xECFEFF -LineRgb $teal -FontSize 17 -Bold
    $mgmtUi = Add-Rect -Slide $slide -Left 38 -Top 325 -Width 115 -Height 64 -Text "Management UI" -FillRgb 0xF8FAFC -LineRgb 0x94A3B8 -FontSize 18 -Bold
    Add-Arrow -Slide $slide -X1 153 -Y1 197 -X2 200 -Y2 150 -Rgb 0x64748B | Out-Null
    Add-Arrow -Slide $slide -X1 153 -Y1 197 -X2 200 -Y2 255 -Rgb 0x64748B | Out-Null
    Add-Arrow -Slide $slide -X1 153 -Y1 357 -X2 200 -Y2 255 -Rgb 0x64748B | Out-Null
    Add-Arrow -Slide $slide -X1 360 -Y1 150 -X2 418 -Y2 190 -Rgb $accentDark | Out-Null
    Add-Arrow -Slide $slide -X1 360 -Y1 255 -X2 418 -Y2 230 -Rgb 0xBE123C | Out-Null
    Add-Arrow -Slide $slide -X1 593 -Y1 190 -X2 653 -Y2 127 -Rgb 0x2563EB | Out-Null
    Add-Arrow -Slide $slide -X1 593 -Y1 209 -X2 653 -Y2 242 -Rgb $green | Out-Null
    Add-Arrow -Slide $slide -X1 506 -Y1 253 -X2 506 -Y2 365 -Rgb $teal | Out-Null
    Add-Arrow -Slide $slide -X1 506 -Y1 365 -X2 653 -Y2 357 -Rgb $teal | Out-Null
    Add-Textbox -Slide $slide -Left 40 -Top 430 -Width 840 -Height 54 -Text "Lecture rapide: Envoy protège et dirige le trafic, Quarkus décide et enregistre les faits métier, PostgreSQL conserve l'historique, et l'observabilité supervise l'ensemble." -FontSize 16 -Rgb 0x475569 | Out-Null
    Add-SlideNumber -Slide $slide -Number $slideIndex

    $slideIndex++
    $slide = $presentation.Slides.Add($slideIndex, 12)
    $slide.Background.Fill.ForeColor.RGB = 0xFFFFFF
    Add-Title -Slide $slide -Title "3. Parcours D'Une Requête LLM" -Subtitle "Chemin nominal sur POST /llm/v1/chat/completions"
    $steps = @(
        "1. Le client appelle Envoy public avec x-customer-id, x-api-key-id et un body OpenAI-compatible contenant model.",
        "2. Envoy applique le rate limiting local et rejette immédiatement en 429 si le quota est dépassé.",
        "3. GrayFile valide que customer, api key et model existent et sont actifs.",
        "4. L'orchestrateur résout les backends actifs via ModelRoutingService et choisit un candidat pondéré.",
        "5. BackendGateway appelle le backend via Envoy egress, qui porte retries, timeout global et circuit breaker.",
        "6. À la réponse, UsageCaptureService lit usage.prompt_tokens, usage.completion_tokens et usage.total_tokens.",
        "7. BillingService persiste l'événement d'usage puis ouvre, met à jour ou clôture une billing window dans la même transaction.",
        "8. Les métriques et les logs d'audit sont enrichis, puis la réponse backend est renvoyée au client avec x-request-id et x-backend-id."
    )
    Add-Bullets -Slide $slide -Left 46 -Top 110 -Width 820 -Height 360 -Items $steps -FontSize 17 | Out-Null
    Add-SlideNumber -Slide $slide -Number $slideIndex

    $slideIndex++
    $slide = $presentation.Slides.Add($slideIndex, 12)
    $slide.Background.Fill.ForeColor.RGB = 0xFFFFFF
    Add-Title -Slide $slide -Title "4. Répartition Des Responsabilités" -Subtitle "Pourquoi certains sujets restent dans Envoy et d'autres dans GrayFile"
    Add-Rect -Slide $slide -Left 48 -Top 120 -Width 245 -Height 245 -Text "Envoy`r`n`r`n• Séparation listeners public/management`r`n• JWT et RBAC sur le management`r`n• Rate limiting et surcharge`r`n• Retries, timeouts, circuit breakers`r`n• Signaux edge: 401/403/404/429, retry, timeout" -FillRgb $softAmber -LineRgb $accent -FontSize 17 -Bold | Out-Null
    Add-Rect -Slide $slide -Left 323 -Top 120 -Width 245 -Height 245 -Text "GrayFile Gateway`r`n`r`n• Validation du scope métier`r`n• Routage dynamique par modèle`r`n• Capture du usage`r`n• Persistance atomique usage + billing`r`n• Audit des décisions et changements" -FillRgb $softBlue -LineRgb $blue -FontSize 17 -Bold | Out-Null
    Add-Rect -Slide $slide -Left 598 -Top 120 -Width 245 -Height 245 -Text "Persistance et observabilité`r`n`r`n• PostgreSQL = source de vérité`r`n• Audit append-only`r`n• Export périodique immuable`r`n• Prometheus = métriques`r`n• Grafana = dashboards opérationnels" -FillRgb $softGreen -LineRgb $green -FontSize 17 -Bold | Out-Null
    Add-Textbox -Slide $slide -Left 48 -Top 395 -Width 800 -Height 62 -Text "Point clé: les invariants d'auditabilité et d'atomicité ne doivent pas être déplacés dans Envoy. La proxy edge protège le système, mais seule GrayFile produit le fait métier billable." -FontSize 17 -Rgb 0x334155 | Out-Null
    Add-SlideNumber -Slide $slide -Number $slideIndex

    $slideIndex++
    $slide = $presentation.Slides.Add($slideIndex, 12)
    $slide.Background.Fill.ForeColor.RGB = 0xF8FAFC
    Add-Title -Slide $slide -Title "5. Zoom Sur Les Composants Internes De La Gateway" -Subtitle "Plan logique du module Quarkus"
    Add-Rect -Slide $slide -Left 38 -Top 120 -Width 175 -Height 62 -Text "LlmProxyResource`r`nEntrée LLM" -FillRgb $softBlue -LineRgb $blue -FontSize 18 -Bold | Out-Null
    Add-Rect -Slide $slide -Left 255 -Top 120 -Width 195 -Height 62 -Text "InferenceOrchestrator`r`nCoordination du flux" -FillRgb $softBlue -LineRgb $blue -FontSize 18 -Bold | Out-Null
    Add-Rect -Slide $slide -Left 492 -Top 80 -Width 165 -Height 62 -Text "ModelRoutingService" -FillRgb 0xDBEAFE -LineRgb 0x2563EB -FontSize 18 -Bold | Out-Null
    Add-Rect -Slide $slide -Left 492 -Top 165 -Width 165 -Height 62 -Text "BackendGateway" -FillRgb 0xDBEAFE -LineRgb 0x2563EB -FontSize 18 -Bold | Out-Null
    Add-Rect -Slide $slide -Left 492 -Top 250 -Width 165 -Height 62 -Text "UsageCaptureService" -FillRgb 0xDBEAFE -LineRgb 0x2563EB -FontSize 18 -Bold | Out-Null
    Add-Rect -Slide $slide -Left 700 -Top 250 -Width 150 -Height 62 -Text "BillingService" -FillRgb $softGreen -LineRgb $green -FontSize 18 -Bold | Out-Null
    Add-Rect -Slide $slide -Left 700 -Top 165 -Width 150 -Height 62 -Text "AuditLogService" -FillRgb $softGreen -LineRgb $green -FontSize 18 -Bold | Out-Null
    Add-Rect -Slide $slide -Left 700 -Top 80 -Width 150 -Height 62 -Text "GatewayMetrics" -FillRgb 0xECFEFF -LineRgb $teal -FontSize 18 -Bold | Out-Null
    Add-Rect -Slide $slide -Left 255 -Top 360 -Width 195 -Height 62 -Text "ManagementService`r`nplan de contrôle" -FillRgb $softRose -LineRgb 0xE11D48 -FontSize 18 -Bold | Out-Null
    Add-Arrow -Slide $slide -X1 213 -Y1 151 -X2 255 -Y2 151 -Rgb $blue | Out-Null
    Add-Arrow -Slide $slide -X1 450 -Y1 151 -X2 492 -Y2 111 -Rgb 0x2563EB | Out-Null
    Add-Arrow -Slide $slide -X1 450 -Y1 151 -X2 492 -Y2 196 -Rgb 0x2563EB | Out-Null
    Add-Arrow -Slide $slide -X1 450 -Y1 151 -X2 492 -Y2 281 -Rgb 0x2563EB | Out-Null
    Add-Arrow -Slide $slide -X1 657 -Y1 111 -X2 700 -Y2 111 -Rgb $teal | Out-Null
    Add-Arrow -Slide $slide -X1 657 -Y1 196 -X2 700 -Y2 196 -Rgb $green | Out-Null
    Add-Arrow -Slide $slide -X1 657 -Y1 281 -X2 700 -Y2 281 -Rgb $green | Out-Null
    Add-Arrow -Slide $slide -X1 352 -Y1 360 -X2 352 -Y2 182 -Rgb 0xE11D48 | Out-Null
    Add-Textbox -Slide $slide -Left 42 -Top 448 -Width 820 -Height 48 -Text "Le flux LLM traverse surtout LlmProxyResource → InferenceOrchestrator → ModelRoutingService / BackendGateway → UsageCaptureService → BillingService. En parallèle, AuditLogService et GatewayMetrics capturent les preuves et indicateurs." -FontSize 15 -Rgb 0x475569 | Out-Null
    Add-SlideNumber -Slide $slide -Number $slideIndex

    $slideIndex++
    $slide = $presentation.Slides.Add($slideIndex, 12)
    $slide.Background.Fill.ForeColor.RGB = 0xFFFFFF
    Add-Title -Slide $slide -Title "6. Routage Dynamique Vers Les Backends" -Subtitle "Gestion des routes par modèle, pondération et garde-fous"
    Add-Bullets -Slide $slide -Left 46 -Top 108 -Width 510 -Height 310 -Items @(
        "Le mapping modèle → backends est stocké dans la table model_routes.",
        "Chaque route porte model_id, backend_id, base_url, weight, active, version et updated_at.",
        "ModelRoutingService charge les routes actives, les met en cache quelques secondes, puis choisit un backend selon les poids.",
        "L'ordre retourné est utile pour tenter d'autres candidats si le premier backend échoue côté application.",
        "Les mises à jour passent par ManagementService, qui invalide le cache via un événement ModelRoutesChangedEvent."
    ) -FontSize 17 | Out-Null
    Add-Rect -Slide $slide -Left 600 -Top 120 -Width 220 -Height 65 -Text "Guardrail 1`r`nBase URL absolue http(s)" -FillRgb $softAmber -LineRgb $accent -FontSize 17 -Bold | Out-Null
    Add-Rect -Slide $slide -Left 600 -Top 205 -Width 220 -Height 65 -Text "Guardrail 2`r`nHealthcheck avant activation" -FillRgb $softAmber -LineRgb $accent -FontSize 17 -Bold | Out-Null
    Add-Rect -Slide $slide -Left 600 -Top 290 -Width 220 -Height 65 -Text "Guardrail 3`r`nAu moins une route active" -FillRgb $softAmber -LineRgb $accent -FontSize 17 -Bold | Out-Null
    Add-Rect -Slide $slide -Left 600 -Top 375 -Width 220 -Height 65 -Text "Guardrail 4`r`nAudit et versionnement des changements" -FillRgb $softAmber -LineRgb $accent -FontSize 16 -Bold | Out-Null
    Add-SlideNumber -Slide $slide -Number $slideIndex

    $slideIndex++
    $slide = $presentation.Slides.Add($slideIndex, 12)
    $slide.Background.Fill.ForeColor.RGB = 0xFFFFFF
    Add-Title -Slide $slide -Title "7. Metering Et Fenêtres De Facturation" -Subtitle "Comment l'usage est transformé en unités de billing auditables"
    Add-Rect -Slide $slide -Left 40 -Top 145 -Width 145 -Height 62 -Text "Réponse backend`r`navec usage" -FillRgb 0xDBEAFE -LineRgb 0x2563EB -FontSize 18 -Bold | Out-Null
    Add-Rect -Slide $slide -Left 218 -Top 145 -Width 165 -Height 62 -Text "UsageCaptureService`r`nvalide les tokens" -FillRgb $softBlue -LineRgb $blue -FontSize 17 -Bold | Out-Null
    Add-Rect -Slide $slide -Left 416 -Top 145 -Width 150 -Height 62 -Text "usage_events`r`npersistés" -FillRgb $softGreen -LineRgb $green -FontSize 18 -Bold | Out-Null
    Add-Rect -Slide $slide -Left 599 -Top 145 -Width 150 -Height 62 -Text "billing_windows`r`nmises à jour" -FillRgb $softGreen -LineRgb $green -FontSize 18 -Bold | Out-Null
    Add-Rect -Slide $slide -Left 782 -Top 145 -Width 95 -Height 62 -Text "Audit" -FillRgb 0xECFEFF -LineRgb $teal -FontSize 18 -Bold | Out-Null
    Add-Arrow -Slide $slide -X1 185 -Y1 176 -X2 218 -Y2 176 -Rgb $blue | Out-Null
    Add-Arrow -Slide $slide -X1 383 -Y1 176 -X2 416 -Y2 176 -Rgb $green | Out-Null
    Add-Arrow -Slide $slide -X1 566 -Y1 176 -X2 599 -Y2 176 -Rgb $green | Out-Null
    Add-Arrow -Slide $slide -X1 749 -Y1 176 -X2 782 -Y2 176 -Rgb $teal | Out-Null
    Add-Bullets -Slide $slide -Left 55 -Top 255 -Width 790 -Height 200 -Items @(
        "Le scope de facturation est (customer_id, api_key_id, model).",
        "Chaque événement d'usage est dédupliqué par clé métier incluant request_id.",
        "Une fenêtre active se ferme au premier seuil atteint: 1000 tokens ou 10 minutes.",
        "En cas de dépassement, le surplus de tokens est reporté immédiatement sur une nouvelle fenêtre.",
        "Un scheduler ferme toutes les 30 secondes les fenêtres expirées par le temps.",
        "L'enregistrement usage + transition de fenêtre est transactionnel pour éviter les états partiels."
    ) -FontSize 17 | Out-Null
    Add-SlideNumber -Slide $slide -Number $slideIndex

    $slideIndex++
    $slide = $presentation.Slides.Add($slideIndex, 12)
    $slide.Background.Fill.ForeColor.RGB = 0xFFFFFF
    Add-Title -Slide $slide -Title "8. Plan De Contrôle Management" -Subtitle "Gestion des clients, modèles, clés API et routes"
    Add-Bullets -Slide $slide -Left 42 -Top 110 -Width 500 -Height 300 -Items @(
        "Le listener Envoy management isole /management/v1/* sur le port 11001.",
        "L'accès nécessite un JWT valide, une identité de service autorisée et une IP interne/VPN autorisée.",
        "ManagementResource expose les opérations CRUD sur customers, models, api-keys, billing-windows, audit-events et model routes.",
        "ManagementService applique des règles métier de cohérence avant persistance."
    ) -FontSize 17 | Out-Null
    Add-Rect -Slide $slide -Left 590 -Top 122 -Width 250 -Height 58 -Text "x-actor-id / x-request-id / x-change-reason" -FillRgb $softRose -LineRgb 0xE11D48 -FontSize 16 -Bold | Out-Null
    Add-Rect -Slide $slide -Left 590 -Top 198 -Width 250 -Height 58 -Text "x-second-approver-id si two-person rule activée" -FillRgb $softRose -LineRgb 0xE11D48 -FontSize 15 -Bold | Out-Null
    Add-Rect -Slide $slide -Left 590 -Top 274 -Width 250 -Height 58 -Text "Alertes si changement sensible massif" -FillRgb $softRose -LineRgb 0xE11D48 -FontSize 16 -Bold | Out-Null
    Add-Rect -Slide $slide -Left 590 -Top 350 -Width 250 -Height 58 -Text "AuditManagementChange sur chaque mutation" -FillRgb $softRose -LineRgb 0xE11D48 -FontSize 16 -Bold | Out-Null
    Add-SlideNumber -Slide $slide -Number $slideIndex

    $slideIndex++
    $slide = $presentation.Slides.Add($slideIndex, 12)
    $slide.Background.Fill.ForeColor.RGB = 0xFFFFFF
    Add-Title -Slide $slide -Title "9. Auditabilité, Sécurité Et Export" -Subtitle "Preuves métier et contrôle d'intégrité"
    Add-Bullets -Slide $slide -Left 48 -Top 110 -Width 520 -Height 320 -Items @(
        "Chaque événement d'audit stocke: type, acteur, entité, payload JSON, horodatage, prev_hash, event_hash et signature.",
        "Le journal audit_log est append-only: update et delete sont bloqués par trigger.",
        "Le chaînage prev_hash → event_hash rend la séquence falsifiable en cas de mutation.",
        "AuditExportService exporte par batch les événements vers un object store immuable en NDJSON signé et checksummé.",
        "Cette chaîne complète soutient l'objectif principal du produit: une facturation vérifiable."
    ) -FontSize 17 | Out-Null
    Add-Rect -Slide $slide -Left 612 -Top 122 -Width 210 -Height 65 -Text "audit_log`r`nappend-only" -FillRgb $softGreen -LineRgb $green -FontSize 18 -Bold | Out-Null
    Add-Rect -Slide $slide -Left 612 -Top 215 -Width 210 -Height 65 -Text "event_hash + signature`r`nintégrité" -FillRgb 0xECFEFF -LineRgb $teal -FontSize 18 -Bold | Out-Null
    Add-Rect -Slide $slide -Left 612 -Top 308 -Width 210 -Height 65 -Text "audit export scheduler`r`n15 min par défaut" -FillRgb $softAmber -LineRgb $accent -FontSize 17 -Bold | Out-Null
    Add-SlideNumber -Slide $slide -Number $slideIndex

    $slideIndex++
    $slide = $presentation.Slides.Add($slideIndex, 12)
    $slide.Background.Fill.ForeColor.RGB = 0xFFFFFF
    Add-Title -Slide $slide -Title "10. Observabilité Et Résilience" -Subtitle "Ce qu'il faut superviser pour exploiter la gateway"
    Add-Bullets -Slide $slide -Left 44 -Top 110 -Width 430 -Height 320 -Items @(
        "Métriques gateway: latence par modèle/customer/api key et backend_status.",
        "Compteurs d'erreurs edge: retry, timeout, circuit_open, upstream_unavailable.",
        "Compteurs applicatifs: erreurs de validation et backend_status >= 400.",
        "Métriques billing: usage_events_total, billing_windows_closed_total, billable_tokens_total.",
        "Grafana est préprovisionné avec un dashboard GrayFile Overview."
    ) -FontSize 17 | Out-Null
    Add-Rect -Slide $slide -Left 520 -Top 118 -Width 300 -Height 68 -Text "Envoy egress`r`ntimeout 5s, 2 retries, per try 2s" -FillRgb $softAmber -LineRgb $accent -FontSize 18 -Bold | Out-Null
    Add-Rect -Slide $slide -Left 520 -Top 215 -Width 300 -Height 68 -Text "Circuit breaker backend`r`n100 connexions, 100 pending, 200 requests" -FillRgb $softAmber -LineRgb $accent -FontSize 17 -Bold | Out-Null
    Add-Rect -Slide $slide -Left 520 -Top 312 -Width 300 -Height 68 -Text "Rate limit public`r`n240 rpm par défaut, overrides par tenant" -FillRgb $softAmber -LineRgb $accent -FontSize 17 -Bold | Out-Null
    Add-SlideNumber -Slide $slide -Number $slideIndex

    $slideIndex++
    $slide = $presentation.Slides.Add($slideIndex, 12)
    $slide.Background.Fill.ForeColor.RGB = 0xFFFFFF
    Add-Title -Slide $slide -Title "11. Données Et Liens Entre Tables" -Subtitle "Vue simplifiée du modèle de persistance"
    Add-Rect -Slide $slide -Left 55 -Top 115 -Width 140 -Height 66 -Text "customers" -FillRgb $softGreen -LineRgb $green -FontSize 20 -Bold | Out-Null
    Add-Rect -Slide $slide -Left 55 -Top 220 -Width 140 -Height 66 -Text "api_keys" -FillRgb $softGreen -LineRgb $green -FontSize 20 -Bold | Out-Null
    Add-Rect -Slide $slide -Left 255 -Top 115 -Width 140 -Height 66 -Text "llm_models" -FillRgb $softGreen -LineRgb $green -FontSize 20 -Bold | Out-Null
    Add-Rect -Slide $slide -Left 255 -Top 220 -Width 140 -Height 66 -Text "model_routes" -FillRgb $softGreen -LineRgb $green -FontSize 20 -Bold | Out-Null
    Add-Rect -Slide $slide -Left 480 -Top 115 -Width 165 -Height 66 -Text "usage_events" -FillRgb $softGreen -LineRgb $green -FontSize 20 -Bold | Out-Null
    Add-Rect -Slide $slide -Left 480 -Top 220 -Width 165 -Height 66 -Text "billing_windows" -FillRgb $softGreen -LineRgb $green -FontSize 20 -Bold | Out-Null
    Add-Rect -Slide $slide -Left 710 -Top 165 -Width 160 -Height 66 -Text "audit_log" -FillRgb 0xECFEFF -LineRgb $teal -FontSize 20 -Bold | Out-Null
    Add-Arrow -Slide $slide -X1 125 -Y1 181 -X2 125 -Y2 220 -Rgb $green | Out-Null
    Add-Arrow -Slide $slide -X1 195 -Y1 148 -X2 255 -Y2 148 -Rgb $green | Out-Null
    Add-Arrow -Slide $slide -X1 325 -Y1 181 -X2 325 -Y2 220 -Rgb $green | Out-Null
    Add-Arrow -Slide $slide -X1 195 -Y1 253 -X2 480 -Y2 148 -Rgb $green | Out-Null
    Add-Arrow -Slide $slide -X1 395 -Y1 253 -X2 480 -Y2 253 -Rgb $green | Out-Null
    Add-Arrow -Slide $slide -X1 645 -Y1 148 -X2 710 -Y2 198 -Rgb $teal | Out-Null
    Add-Arrow -Slide $slide -X1 645 -Y1 253 -X2 710 -Y2 198 -Rgb $teal | Out-Null
    Add-Textbox -Slide $slide -Left 55 -Top 360 -Width 805 -Height 86 -Text "Relations métier principales:`r`n• une api key appartient à un customer`r`n• un model possède plusieurs routes backend`r`n• les événements d'usage et les fenêtres de billing sont indexés par customer, api key et model`r`n• audit_log enregistre les changements de configuration et les événements de billing" -FontSize 16 -Rgb 0x334155 | Out-Null
    Add-SlideNumber -Slide $slide -Number $slideIndex

    $slideIndex++
    $slide = $presentation.Slides.Add($slideIndex, 12)
    $slide.Background.Fill.ForeColor.RGB = $themeBg
    Add-Title -Slide $slide -Title "12. Messages À Retenir" -Subtitle "Conclusion"
    Add-Bullets -Slide $slide -Left 60 -Top 130 -Width 780 -Height 260 -Items @(
        "GrayFile n'est pas seulement un proxy: c'est un point de contrôle métier pour la consommation LLM.",
        "La séparation Envoy / Quarkus clarifie l'architecture: edge policy d'un côté, vérité métier et facturation de l'autre.",
        "Le design des composants sert un objectif central: rendre chaque consommation, chaque route et chaque changement auditable.",
        "La plateforme est déjà prête pour une évolution vers plus de backends, plus de règles de routage et davantage d'automatisation."
    ) -FontSize 20 | Out-Null
    Add-Rect -Slide $slide -Left 215 -Top 400 -Width 470 -Height 70 -Text "Synthèse finale: contrôle, traçabilité, résilience et observabilité autour du trafic LLM" -FillRgb $softAmber -LineRgb $accent -FontSize 20 -Bold | Out-Null
    Add-SlideNumber -Slide $slide -Number $slideIndex

    $presentation.SaveAs($absoluteOutputPath)
}
finally {
    if ($presentation) {
        $presentation.Close()
    }
    if ($powerPoint) {
        $powerPoint.Quit()
    }
    [System.Runtime.InteropServices.Marshal]::ReleaseComObject($presentation) | Out-Null
    [System.Runtime.InteropServices.Marshal]::ReleaseComObject($powerPoint) | Out-Null
    [GC]::Collect()
    [GC]::WaitForPendingFinalizers()
}

Write-Output "Presentation generated: $absoluteOutputPath"
