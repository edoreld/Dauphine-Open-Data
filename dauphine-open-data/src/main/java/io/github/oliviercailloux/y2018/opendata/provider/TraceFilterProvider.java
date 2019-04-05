package io.github.oliviercailloux.y2018.opendata.provider;

import java.security.Principal;
import java.util.Date;
import java.util.Optional;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.ext.Provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

import io.github.oliviercailloux.y2018.opendata.dao.EntityAlreadyExistsDaoException;
import io.github.oliviercailloux.y2018.opendata.dao.HttpAuditDao;
import io.github.oliviercailloux.y2018.opendata.entity.HttpAudit;
import io.github.oliviercailloux.y2018.opendata.entity.HttpAudit.HttpMethod;
import io.github.oliviercailloux.y2018.opendata.service.DateService;

@Provider
public class TraceFilterProvider implements ContainerRequestFilter {

	private static final Logger LOGGER = LoggerFactory.getLogger(TraceFilterProvider.class);
	
	@Inject
	private DateService dateService;
	
	@Inject
	private HttpAuditDao httpAuditDao;

	@PostConstruct
	public void postConstruct() {
		Preconditions.checkNotNull(dateService, "dateService");
		Preconditions.checkNotNull(httpAuditDao, "httpAuditDao");
	}
	
	private void setUserIfAuthenticated(HttpAudit httpAudit, ContainerRequestContext requestContext) {
		final SecurityContext sc = requestContext.getSecurityContext();
		if(sc != null) {
			final Principal p = requestContext.getSecurityContext().getUserPrincipal();
			if(p != null) {
				httpAudit.setUser(p.getName());
			}
		}
	}
	
	private Optional<HttpAudit> makeHttpAudit(ContainerRequestContext requestContext, DateService dateService) {
		final HttpMethod httpMethod;
		try {
			httpMethod = HttpMethod.from(requestContext.getMethod());
		} catch(IllegalArgumentException e) {
			LOGGER.error("cannot parse http method {}", requestContext.getMethod(), e);
			return Optional.empty();
		}
		final Date date = dateService.getCurrentDate();
		final String path = requestContext.getUriInfo().getPath();
		final HttpAudit httpAudit = new HttpAudit(date, path, httpMethod);
		setUserIfAuthenticated(httpAudit, requestContext);
		return Optional.of(httpAudit);
	}

	private Optional<HttpAudit> makeHttpAudit(ContainerRequestContext requestContext) {
		return makeHttpAudit(requestContext, dateService);
	}
	
	@Override
	public void filter(ContainerRequestContext requestContext) {		
		try {
			final Optional<HttpAudit> httpAuditOpt = makeHttpAudit(requestContext);
			httpAuditOpt.ifPresent(httpAuditDao::persist);
		} catch (EntityAlreadyExistsDaoException e) {
			LOGGER.error("failed to insert HttpAudit", e);
		}
	}

}