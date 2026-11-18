package vn.antam.clinic.config.demo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import vn.antam.clinic.service.demo.MockDataSeeder;
import vn.antam.clinic.service.demo.UpgradeFeaturesSeeder;
import vn.antam.clinic.service.demo.VolumeDemoSeeder;

@Component
@ConditionalOnProperty(name = "app.mock-data.enabled", havingValue = "true")
public class MockDataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MockDataInitializer.class);
    private final MockDataSeeder seeder;
    private final UpgradeFeaturesSeeder upgrade;
    private final VolumeDemoSeeder volume;
    private final boolean volumeEnabled;

    public MockDataInitializer(
        MockDataSeeder seeder,
        UpgradeFeaturesSeeder upgrade,
        VolumeDemoSeeder volume,
        @Value("${app.mock-data.volume-enabled:false}") boolean volumeEnabled
    ) {
        this.seeder = seeder;
        this.upgrade = upgrade;
        this.volume = volume;
        this.volumeEnabled = volumeEnabled;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean seeded = seeder.initialize();
        boolean upgraded = upgrade.initialize();
        boolean expanded = volumeEnabled && volume.initialize();
        if (expanded) log.info(
            "Đã thêm bộ demo dung lượng lớn: bệnh nhân/người thân, lịch 30 ngày, lượt khám 12 tuần và các nghiệp vụ liên quan."
        );
        if (seeded || upgraded || expanded) {
            log.info(
                "Đã khởi tạo hoặc cập nhật dữ liệu demo: danh mục, mô tả chi tiết, ảnh bác sĩ và luồng ngoại trú."
            );
        } else {
            log.info("Dữ liệu demo đã được khởi tạo; giữ nguyên các thao tác đã thực hiện.");
        }
    }
}
