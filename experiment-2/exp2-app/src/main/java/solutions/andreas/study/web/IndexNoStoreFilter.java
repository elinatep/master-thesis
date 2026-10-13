package solutions.andreas.study.web;

import java.io.IOException;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Stops the browser caching the SPA's entry point.
 *
 * <p>The portal library serves {@code index.html} for every path that is not a real file, so a
 * participant's browser can hold on to it. Its only content is a script tag naming a hashed bundle
 * - {@code index-Dai3fM33.js} - and that name changes with every build. A cached index.html
 * therefore points at a bundle the deployment no longer has, which is a blank page for the
 * participant and nothing in any log to explain it.
 *
 * <p>It also means a redeploy mid-study can leave some participants on the previous version of the
 * study, which is worse: they finish, their data looks ordinary, and nothing records that they were
 * running different software from everyone else.
 *
 * <p>The hashed assets stay cacheable, which is the point of hashing them: their names change when
 * their contents do. Only the one file that keeps its name is made uncacheable.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class IndexNoStoreFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (request instanceof HttpServletRequest http && response instanceof HttpServletResponse res
                && isSpaEntryPoint(http)) {
            res.setHeader("Cache-Control", "no-store, must-revalidate");
            res.setHeader("Pragma", "no-cache");
        }
        chain.doFilter(request, response);
    }

    /**
     * True for the paths that are answered with index.html.
     *
     * <p>Decided by what the path looks like rather than by what the response turns out to be: the
     * headers have to be set before anything is written, and by then the resource handler has
     * already chosen. Anything with a file extension is a real asset - hashed, and cacheable - and
     * anything under /api is not a page at all.
     */
    static boolean isSpaEntryPoint(HttpServletRequest request) {
        return isSpaEntryPoint(request.getRequestURI());
    }

    static boolean isSpaEntryPoint(String path) {
        if (path.startsWith("/api/")) {
            return false;
        }
        int lastSlash = path.lastIndexOf('/');
        String last = lastSlash < 0 ? path : path.substring(lastSlash + 1);
        return last.isEmpty() || last.indexOf('.') < 0 || last.equals("index.html");
    }
}
