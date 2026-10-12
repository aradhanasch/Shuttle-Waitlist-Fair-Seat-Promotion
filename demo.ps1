# Sample API calls. Start the app first: .\mvnw.cmd spring-boot:run
$base  = "http://localhost:8080"
$json  = "application/json"
$admin = @{ "X-User" = "admin"; "X-Role" = "ADMIN" }
$e1 = @{ "X-User" = "emp1"; "X-Role" = "EMPLOYEE" }
$e2 = @{ "X-User" = "emp2"; "X-Role" = "EMPLOYEE" }
$e3 = @{ "X-User" = "emp3"; "X-Role" = "EMPLOYEE" }

Write-Host "`n1. Admin creates a 1-seat trip"
$trip = Invoke-RestMethod -Method Post "$base/trips" -Headers $admin -ContentType $json `
  -Body '{"capacity":1,"checkInCutoff":"2030-01-01T00:00:00Z","quotas":{"CC1":5}}'
$trip
$id = $trip.id

Write-Host "`n2. emp1 gets the seat; emp2 (group of 2) and emp3 are waitlisted"
$b1 = Invoke-RestMethod -Method Post "$base/trips/$id/bookings" -Headers $e1 -ContentType $json -Body '{"costCentre":"CC1","partySize":1}'
$b1
Invoke-RestMethod -Method Post "$base/trips/$id/bookings" -Headers $e2 -ContentType $json -Body '{"costCentre":"CC1","partySize":2}'
Invoke-RestMethod -Method Post "$base/trips/$id/bookings" -Headers $e3 -ContentType $json -Body '{"costCentre":"CC1","partySize":1}'

Write-Host "`n3. Waitlist (approver view)"
Invoke-RestMethod "$base/trips/$id/waitlist" -Headers $admin | Format-Table

Write-Host "`n4. emp1 cancels: group of 2 is skipped, emp3 is promoted (see SMS line in app log)"
Invoke-RestMethod -Method Delete "$base/bookings/$($b1.id)" -Headers $e1

Write-Host "`n5. Waitlist and availability afterwards (availability is cached up to 5 s)"
Invoke-RestMethod "$base/trips/$id/waitlist" -Headers $admin | Format-Table
Start-Sleep -Seconds 6
Invoke-RestMethod "$base/trips/$id" -Headers $e1

Write-Host "`n6. Metrics"
(Invoke-RestMethod "$base/actuator/metrics/shuttle.promotions").measurements

Write-Host "`n7. Employee tries an admin action -> 403 with a clean JSON error"
try {
  Invoke-RestMethod -Method Post "$base/trips" -Headers $e1 -ContentType $json -Body '{"capacity":1,"checkInCutoff":"2030-01-01T00:00:00Z","quotas":{"CC1":1}}'
} catch { $_.ErrorDetails.Message }
