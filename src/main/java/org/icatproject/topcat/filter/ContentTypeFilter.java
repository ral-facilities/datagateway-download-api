package org.icatproject.topcat.filter;

import java.io.IOException;
import java.util.List;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.ext.Provider;

/**
 * 
 * This is a nasty hack suggested by Carlo Pellegrini to remove any parameters -
 * such as charset=UTF-8 - from the content type. This code makes the reasonable
 * assumption that only one content type header is present.
 * 
 * This is needed because of a bug in Jersey 2.0 which is supposedly fixed in
 * 2.2.
 *
 * TODO remove this class when not needed.
 *
 */
@Provider
@PreMatching
public class ContentTypeFilter implements ContainerRequestFilter {

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        MultivaluedMap<String, String> headers = requestContext.getHeaders();
        List<String> contentTypes = headers.remove(HttpHeaders.CONTENT_TYPE);
        if (contentTypes != null) {
            String contentType = contentTypes.get(0);
            String sanitizedContentType = contentType.replaceFirst(";.*", "");
            headers.add(HttpHeaders.CONTENT_TYPE, sanitizedContentType);
        }
    }
}
