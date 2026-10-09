package com.stylemind.identity.directory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class VietnamAdministrativeDirectoryTests {
    private final VietnamAdministrativeDirectory directory = new VietnamAdministrativeDirectory(new ObjectMapper());

    @Test
    void pinnedDatasetLoadsImmutableSortedProjectionsAndKeepsCodesAsStrings() {
        var provinces = directory.provinces();
        assertThat(provinces.datasetVersion()).isEqualTo("v5.2.0");
        assertThat(provinces.provinces()).hasSize(34);
        assertThat(provinces.provinces()).isSortedAccordingTo(java.util.Comparator.comparing(p -> p.code()));
        assertThat(directory.wards("01").wards()).isNotEmpty();
        assertThat(directory.wards("01").wards()).isSortedAccordingTo(java.util.Comparator.comparing(w -> w.code()));
        assertThat(directory.containsWard("00004")).isTrue();
        assertThatThrownBy(() -> provinces.provinces().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void malformedDatasetsFailForDuplicateCodesAndOrphanWards() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String valid = "{\"metadata\":{\"dataset_version\":\"v\",\"latest_decree\":\"d\","
                + "\"generated_at\":\"t\"},\"provinces\":[{\"code\":\"01\",\"name\":\"P\"}],"
                + "\"wards\":[{\"code\":\"00001\",\"name\":\"W\",\"province_code\":\"01\"}]}";
        var root = mapper.readTree(valid);
        assertThat(VietnamAdministrativeDirectory.parse(root).wardCodes()).containsKey("00001");
        var duplicateProvince = mapper.readTree(valid.replace("}],\"wards\"", "},{\"code\":\"01\",\"name\":\"P2\"}],\"wards\""));
        assertThatThrownBy(() -> VietnamAdministrativeDirectory.parse(duplicateProvince))
                .isInstanceOf(IllegalArgumentException.class);
        var orphanWard = mapper.readTree(valid.replace("\"province_code\":\"01\"", "\"province_code\":\"99\""));
        assertThatThrownBy(() -> VietnamAdministrativeDirectory.parse(orphanWard))
                .isInstanceOf(IllegalArgumentException.class);
        var duplicateWard = mapper.readTree(valid.replace("}],\"wards\"", "}],\"wards\"")
                .replace("]}", ",{\"code\":\"00001\",\"name\":\"W2\",\"province_code\":\"01\"}]}"));
        assertThatThrownBy(() -> VietnamAdministrativeDirectory.parse(duplicateWard))
                .isInstanceOf(IllegalArgumentException.class);
        var blankName = mapper.readTree(valid.replace("\"name\":\"P\"", "\"name\":\"  \""));
        assertThatThrownBy(() -> VietnamAdministrativeDirectory.parse(blankName))
                .isInstanceOf(IllegalArgumentException.class);
        var numericWardCode = mapper.readTree(valid.replace("\"code\":\"00001\"", "\"code\":1"));
        assertThatThrownBy(() -> VietnamAdministrativeDirectory.parse(numericWardCode))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
