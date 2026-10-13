package solutions.andreas.study.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Which paths get the no-store header.
 *
 * <p>The cost of getting this wrong runs both ways. Too narrow and a participant keeps a stale
 * index.html pointing at a bundle that no longer exists - a blank page, with nothing in any log.
 * Too wide and the hashed assets stop being cached, so every page load re-downloads two megabytes
 * of JavaScript on a home connection.
 */
class IndexNoStoreFilterTest {

    @Test
    void thePagesParticipantsLandOnAreNotCached() {
        for (String path : new String[] { "/", "/index.html", "/outcome", "/policies", "/claims/12", "/agent", "/data" }) {
            assertThat(IndexNoStoreFilter.isSpaEntryPoint(path))
                    .as("%s is answered with index.html", path)
                    .isTrue();
        }
    }

    @Test
    void theHashedAssetsStayCacheable() {
        for (String path : new String[] {
                "/assets/index-Dai3fM33.js",
                "/assets/index-D63nc56X.css",
                "/assets/bootstrap-icons-mSm7cUeB.woff2",
                "/favicon.svg" }) {
            assertThat(IndexNoStoreFilter.isSpaEntryPoint(path))
                    .as("%s is a hashed asset and should keep its cache headers", path)
                    .isFalse();
        }
    }

    @Test
    void theApiIsLeftAlone() {
        for (String path : new String[] { "/api/study/task", "/api/handover/abc", "/api/agent/queue" }) {
            assertThat(IndexNoStoreFilter.isSpaEntryPoint(path)).isFalse();
        }
    }
}
