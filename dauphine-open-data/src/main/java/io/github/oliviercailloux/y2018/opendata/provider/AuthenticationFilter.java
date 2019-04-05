package io.github.oliviercailloux.y2018.opendata.provider;

import io.github.oliviercailloux.y2018.opendata.annotation.Secured;
import io.github.oliviercailloux.y2018.opendata.cas.DauphineCas;

import javax.annotation.Priority;
import javax.inject.Inject;
import javax.ws.rs.Priorities;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.ext.Provider;
import java.io.IOException;
import java.security.Principal;

/**
 * This filter is called before the request is processed and is responsible of authentication based on the bearer token
 */
@Secured
@Provider
@Priority(Priorities.AUTHENTICATION)
public class AuthenticationFilter implements ContainerRequestFilter {

    @Inject
    private DauphineCas dauphineCas;
    
    @Override
    public void filter(ContainerRequestContext requestContext) {
        
        // warning: http header `authorization` contains authentication information, as the name does not suggest
        String authorizationHeader = requestContext.getHeaderString(HttpHeaders.AUTHORIZATION);
        
        // abort the request if not using authentication token
        if (authorizationHeader == null || !authorizationHeader.toLowerCase().startsWith("bearer ")) {
            abortRequest(requestContext);
            return ;
        }
        
        // extract the token
        String token = authorizationHeader.substring("bearer".length()).trim();

        try {
            String username = dauphineCas.validateToken(token);
            attachUsernameToContext(username, requestContext);
        }
        catch (Exception e) {
            // abort request if the CAS has not validated the token
            abortRequest(requestContext);
        }
    }
    
    private void attachUsernameToContext(String username, ContainerRequestContext requestContext) {
        final SecurityContext currentSecurityContext = requestContext.getSecurityContext();
        requestContext.setSecurityContext(new SecurityContext() {
        
            @Override
            public Principal getUserPrincipal() {
                return () -> username;
            }
        
            @Override
            public boolean isUserInRole(String role) {
                // TODO: Role based access
                return true;
            }
        
            @Override
            public boolean isSecure() {
                return currentSecurityContext.isSecure();
            }
        
            @Override
            public String getAuthenticationScheme() {
                return "Bearer";
            }
        });
    }
    
    private void abortRequest(ContainerRequestContext requestContext) {
        requestContext.abortWith(
                Response.status(Response.Status.UNAUTHORIZED)
                .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer realm=dauphine-data")
                .build()
        );
    }
}