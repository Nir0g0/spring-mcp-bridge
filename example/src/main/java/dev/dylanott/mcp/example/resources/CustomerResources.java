package dev.dylanott.mcp.example.resources;

import dev.dylanott.mcp.annotation.MCPResource;
import org.springframework.stereotype.Component;

@Component
public class CustomerResources {

    @MCPResource(
            uri = "db://customers/{region}",
            description = "Active customers in a region",
            mimeType = "application/json",
            query = "SELECT id, name, region, signup_date FROM customer "
                    + "WHERE region = :region ORDER BY signup_date DESC",
            roles = {"analyst"}
    )
    public void customersInRegion() {
        // Body intentionally empty: the query attribute drives execution.
    }

    @MCPResource(
            uri = "db://customers/{id}/invoices",
            description = "Invoices for a customer, newest first",
            mimeType = "application/json",
            query = "SELECT id, amount_cents, issued_at FROM invoice "
                    + "WHERE customer_id = :id ORDER BY issued_at DESC",
            roles = {"analyst"}
    )
    public void invoicesForCustomer() {
    }
}
