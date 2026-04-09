$ErrorActionPreference = "Stop"

$composeFile = "deploy/docker-compose.yml"

docker compose -f $composeFile up -d --build

try {
    Start-Sleep -Seconds 10

    Invoke-RestMethod -Method Post -Uri "http://localhost:8080/management/v1/customers" -ContentType "application/json" -Body '{"id":"customer-1","name":"Acme"}' | Out-Null
    Invoke-RestMethod -Method Post -Uri "http://localhost:8080/management/v1/models" -ContentType "application/json" -Body '{"id":"gpt-4o-mini","displayName":"GPT-4o Mini","provider":"openai"}' | Out-Null
    Invoke-RestMethod -Method Post -Uri "http://localhost:8080/management/v1/api-keys" -ContentType "application/json" -Body '{"id":"key-1","customerId":"customer-1","name":"Primary"}' | Out-Null

    $headers = @{
        "x-customer-id" = "customer-1"
        "x-api-key-id" = "key-1"
        "x-llm-model" = "gpt-4o-mini"
    }
    $body = '{"model":"gpt-4o-mini","messages":[{"role":"user","content":"Hello"}]}'
    $response = Invoke-RestMethod -Method Post -Uri "http://localhost:8080/llm/v1/chat/completions" -Headers $headers -ContentType "application/json" -Body $body

    if ($response.usage.total_tokens -ne 20) {
        throw "Unexpected total_tokens value: $($response.usage.total_tokens)"
    }
}
finally {
    docker compose -f $composeFile down -v
}
