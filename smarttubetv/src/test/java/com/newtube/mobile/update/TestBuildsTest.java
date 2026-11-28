package com.newtube.mobile.update;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import java.util.List;

/** What Settings > About > Test builds reads out of GitHub's releases list. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class TestBuildsTest {
    private static final String RELEASES = "["
            + "{\"tag_name\":\"test-fix-x\",\"name\":\"Test: Fix the thing\",\"prerelease\":true,\"draft\":false,"
            + " \"body\":\"version: 1.17.1-9-test.abc1234\\ncode: 1170109\\nbranch: fix/x\\ncommit: abc1234def\\npr: 31\\n\\nText\","
            + " \"assets\":[{\"name\":\"ReTube_1.17.1-9-test.abc1234_universal.apk\",\"browser_download_url\":\"u-uni\",\"size\":9},"
            + "{\"name\":\"ReTube_1.17.1-9-test.abc1234_arm64-v8a.apk\",\"browser_download_url\":\"u-arm\",\"size\":5,\"created_at\":\"2026-11-28T09:13:11Z\"}]},"
            + "{\"tag_name\":\"dn-1.17.1-9\",\"name\":\"ReTube dn-1.17.1-9\",\"prerelease\":false,\"draft\":false,\"body\":\"\","
            + " \"assets\":[{\"name\":\"ReTube_1.17.1-9_universal.apk\",\"browser_download_url\":\"s-uni\",\"size\":9}]},"
            + "{\"tag_name\":\"v1.5.0\",\"prerelease\":true,\"draft\":false,\"body\":\"\",\"assets\":[]}]";

    @Test
    public void listsOnlyTestPrereleasesWithTheirNotes() throws Exception {
        List<TestBuilds.Build> builds = TestBuilds.parseBuilds(RELEASES, new String[] {"arm64-v8a", "armeabi-v7a"});
        assertEquals(1, builds.size());
        TestBuilds.Build b = builds.get(0);
        assertEquals("Fix the thing", b.title);
        assertEquals("fix/x", b.branch);
        assertEquals("31", b.pr);
        assertEquals(1170109, b.versionCode);
        assertEquals("abc1234", b.shortCommit());
    }

    @Test
    public void picksThePhonesAbiThenUniversal() throws Exception {
        assertEquals("u-arm", TestBuilds.parseBuilds(RELEASES, new String[] {"arm64-v8a"}).get(0).apkUrl);
        assertEquals("u-uni", TestBuilds.parseBuilds(RELEASES, new String[] {"x86"}).get(0).apkUrl);
    }

    @Test
    public void stableIsTheNewestPublishedForkRelease() throws Exception {
        TestBuilds.Build stable = TestBuilds.parseStable(RELEASES, new String[] {"arm64-v8a"});
        assertEquals("1.17.1-9", stable.versionName);
        assertEquals(1170109, stable.versionCode);
        assertEquals("s-uni", stable.apkUrl);
        assertNull(TestBuilds.parseStable("[]", new String[0]));
    }

    @Test
    public void builtTimeIsTheApksUploadTimeInUtc() throws Exception {
        TestBuilds.Build b = TestBuilds.parseBuilds(RELEASES, new String[] {"arm64-v8a"}).get(0);
        assertEquals(1795857191000L, b.createdAt); // 2026-11-28T09:13:11Z
        assertEquals(0, TestBuilds.parseTime("not a time"));
        assertEquals(0, TestBuilds.parseTime(null));
    }

    @Test
    public void notesStopAtTheFirstBlankLine() {
        String[] f = TestBuilds.noteFields("branch: a\n\ncommit: not-a-field");
        assertEquals("a", f[2]);
        assertTrue(f[3].isEmpty());
    }
}
