package vn.antam.clinic.service.impl.demo;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.antam.clinic.service.demo.DemoDescriptionSeeder;

/** A one-time content upgrade, including databases whose original seed already completed. */
@Service
public class DemoDescriptionSeederImpl implements DemoDescriptionSeeder {

    static final String DATASET = "outpatient-descriptions-v2";
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public DemoDescriptionSeederImpl(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    @Transactional
    public boolean initialize() {
        jdbc.update(
            "insert into mock_data_runs(dataset,initialized_on) values(?,?) on duplicate key update dataset=dataset",
            DATASET,
            LocalDate.now(clock)
        );
        if (
            Boolean.TRUE.equals(
                jdbc.queryForObject(
                    "select completed_at is not null from mock_data_runs where dataset=? for update",
                    Boolean.class,
                    DATASET
                )
            )
        ) return false;
        for (int index = 0; index < HospitalDirectorySeederImpl.SPECIALTIES.size(); index++) {
            var fixture = HospitalDirectorySeederImpl.SPECIALTIES.get(index);
            var copy = DemoDirectoryContent.forCode(fixture.code());
            update(
                "departments",
                "MOCK-K-" + fixture.code(),
                copy.department(fixture.name()),
                fixture.description() + " Danh mục minh họa ngoại trú."
            );
            update(
                "specialties",
                "MOCK-CK-" + fixture.code(),
                copy.specialty(fixture.name()),
                fixture.description() + " Dữ liệu demo của Minh An."
            );
            update("rooms", "MOCK-P-" + fixture.code(), copy.room(fixture.name()), null);
            update("services", "MOCK-KB-" + fixture.code(), copy.service(fixture.name()), null);
            for (int member = 0; member < 3; member++) {
                biography(
                    "mockbs%03d".formatted(index * 3 + member + 1),
                    copy,
                    fixture.name(),
                    member,
                    "Hồ sơ bác sĩ giả lập để thử nghiệm Minh An; không đại diện bác sĩ hoặc đơn vị có thật. " +
                        fixture.description()
                );
                photo("mockbs%03d".formatted(index * 3 + member + 1), photo(index, member));
            }
        }
        baseContent();
        jdbc.update(
            "insert into audit_logs(action,entity_type,entity_id,detail) values('MOCK_CONTENT_UPDATE','DEMO',?,?)",
            DATASET,
            "Bổ sung nội dung khoa, chuyên khoa, hồ sơ bác sĩ, phòng và dịch vụ demo; giữ mô tả đã sửa thủ công."
        );
        jdbc.update(
            "update mock_data_runs set completed_at=? where dataset=?",
            Timestamp.from(clock.instant()),
            DATASET
        );
        return true;
    }

    private void baseContent() {
        var internal = DemoDirectoryContent.forCode("NOI");
        var surgical = DemoDirectoryContent.forCode("NGOAI");
        update("departments", "NOI", internal.department("Nội"), "Điều trị nội khoa và nội trú");
        update("departments", "NGOAI", surgical.department("Ngoại"), "Khám và điều trị ngoại khoa");
        update(
            "specialties",
            "NOI",
            internal.specialty("Nội tổng quát"),
            "Khám và điều trị nội khoa"
        );
        update(
            "specialties",
            "NGOAI",
            surgical.specialty("Ngoại tổng quát"),
            "Khám và điều trị ngoại khoa"
        );
        biography("bacsi01", internal, "Nội tổng quát", 0, "Khám nội khoa tổng quát.");
        biography("bacsi02", surgical, "Ngoại tổng quát", 1, "Khám ngoại khoa tổng quát.");
        photo("bacsi01", DemoPortraitSeederImpl.path(1));
        photo("bacsi02", DemoPortraitSeederImpl.path(2));
        update("services", "KB-NOI", internal.service("Nội tổng quát"), null);
        update("services", "KB-NGOAI", surgical.service("Ngoại tổng quát"), null);
        update("rooms", "P101", internal.room("Nội tổng quát"), null);
        update("rooms", "P102", surgical.room("Ngoại tổng quát"), null);

        List<String[]> departments = List.of(
            new String[] {
                "KB",
                "Tiếp nhận và khám ngoại trú",
                "Khoa Khám bệnh hỗ trợ tiếp nhận người bệnh ngoại trú: kiểm tra thông tin định danh, đối chiếu lịch hẹn và hướng dẫn đến đúng phòng. Lễ tân phối hợp với thu ngân, bác sĩ để theo dõi trạng thái từ đặt lịch đến hoàn tất khám. Bộ demo có khách đã đăng ký tài khoản và hồ sơ đặt tại quầy; các hồ sơ không tự gộp theo tên hoặc số điện thoại. Nội dung minh họa vận hành Minh An.",
            },
            new String[] {
                "XN",
                "Lấy mẫu và trả kết quả xét nghiệm",
                "Khoa Xét nghiệm là danh mục kế thừa để minh họa cơ cấu khoa/phòng và tài khoản kỹ thuật viên. Danh mục giúp kiểm tra tìm kiếm, phân quyền và trạng thái hoạt động trong quản trị. Quy trình chỉ định, lấy mẫu, thực hiện và trả kết quả xét nghiệm chưa thuộc luồng ngoại trú demo hiện tại; mô tả này không khẳng định đã cung cấp dịch vụ xét nghiệm.",
            },
            new String[] {
                "DUOC",
                "Kho thuốc và cấp phát",
                "Khoa Dược là danh mục minh họa quản trị. Trong luồng ngoại trú hiện tại, bác sĩ chọn thuốc từ danh mục, lưu đơn theo lượt khám và in để người bệnh mua ngoài. Tiền thuốc không được cộng vào hóa đơn khám. Quản lý tồn kho, xuất kho và cấp phát chưa được vận hành trong bộ demo; tài khoản và phòng dược được giữ để kiểm tra danh mục kế thừa.",
            },
            new String[] {
                "TC",
                "Viện phí và thu ngân",
                "Phòng Tài chính theo dõi phí khám đã ghi nhận, phiếu thu và công nợ. Thu ngân tra cứu hóa đơn, ghi nhận thu từng phần bằng tiền mặt hoặc chuyển khoản thủ công và in phiếu thu; lịch sử thu được giữ để đối chiếu báo cáo. Quyền thu tiền tách riêng với lễ tân và admin. Giá đã chốt cho lịch hẹn không đổi theo giá danh mục; đây là dữ liệu vận hành giả lập.",
            },
            new String[] {
                "QT",
                "Quản trị hệ thống và danh mục",
                "Phòng Quản trị quản lý tài khoản nhân viên, khoa/phòng, chuyên khoa, hồ sơ bác sĩ, dịch vụ và cấu hình cơ sở. Admin giám sát lịch chờ duyệt, dữ liệu báo cáo, nhật ký thao tác và email lỗi; không có quyền thu tiền hoặc sửa bệnh án chỉ vì là admin. Danh mục dùng để kiểm tra cấu hình và phân quyền trong môi trường demo Minh An.",
            }
        );
        for (String[] item : departments) update("departments", item[0], item[2], item[1]);

        for (String code : List.of("N201", "N202", "G301"))
            update(
                "rooms",
                code,
                "Buồng minh họa thuộc danh mục nội trú kế thừa, phục vụ kiểm tra quan hệ khoa - phòng - giường và trạng thái hoạt động. Không dùng buồng này để xếp lịch khám ngoại trú. Tiếp nhận nội trú, phân giường, điều trị theo ngày và xuất viện chưa nằm trong demo hiện tại; thông tin không đại diện bố trí cơ sở thật.",
                null
            );
        update(
            "rooms",
            "XN01",
            "Phòng lấy mẫu minh họa thuộc Khoa Xét nghiệm. Dùng để kiểm tra phân loại phòng, quan hệ khoa và phân quyền danh mục. Quy trình lấy mẫu, thực hiện xét nghiệm và trả kết quả chưa được mở trong luồng demo ngoại trú; không có cam kết thiết bị, thời gian trả kết quả hoặc dịch vụ thật.",
            null
        );
        update(
            "rooms",
            "DUOC01",
            "Quầy thuốc minh họa thuộc Khoa Dược, phục vụ thử danh mục phòng và vai trò dược sĩ. Luồng hiện tại chỉ kê và in đơn để bệnh nhân mua thuốc ngoài, không bán thuốc, cấp phát hoặc quản lý tồn kho tại quầy. Thông tin giả lập này không được dùng như địa chỉ mua thuốc hay dữ liệu tồn kho.",
            null
        );
        update(
            "rooms",
            "TC01",
            "Quầy thu ngân phục vụ demo ghi nhận phí khám. Nhân viên đối chiếu mã lịch, người bệnh và hóa đơn; kiểm tra đã thu/còn thiếu trước khi nhận tiền. Phiếu thu ghi nhận số tiền, phương thức và thời điểm; các lần thu từng phần được lưu riêng. Thu ngân không chỉnh sửa bệnh án hoặc đơn thuốc. Vị trí quầy là dữ liệu giả lập.",
            null
        );
        for (String code : List.of("XN-CTM", "XN-SH"))
            update(
                "services",
                code,
                "Dịch vụ xét nghiệm mẫu trong danh mục kế thừa, dùng để kiểm tra tên, nhóm dịch vụ, đơn vị và lịch sử giá. Không thuộc gói khám ngoại trú và không được tự cộng vào hóa đơn khám. Quy trình chỉ định, lấy mẫu, thực hiện và trả kết quả chưa triển khai trong demo; nội dung này không mô tả thành phần xét nghiệm hoặc cam kết chuyên môn của cơ sở thật.",
                null
            );
        for (String code : List.of("GB-THUONG", "GB-VIP"))
            update(
                "services",
                code,
                "Dịch vụ giường mẫu tính theo ngày, giữ trong danh mục kế thừa để kiểm tra quản trị và lịch sử giá. Không dùng để đặt giờ khám hoặc tính phí ngoại trú. Luồng nhập viện, phân giường, lưu trú và xuất viện thuộc giai đoạn sau; giá mẫu không xác nhận tiêu chuẩn phòng, tiện nghi hoặc dịch vụ nội trú có thật.",
                null
            );
    }

    private void biography(
        String username,
        DemoDirectoryContent.Content copy,
        String specialty,
        int member,
        String original
    ) {
        jdbc.query(
            "select dp.staff_id,u.full_name from doctor_profiles dp join staff s on s.id=dp.staff_id join users u on u.id=s.user_id where u.username=?",
            (rs, row) -> new Object[] { rs.getLong("staff_id"), rs.getString("full_name") },
            username
        ).forEach(row ->
            jdbc.update(
                "update doctor_profiles set bio=? where staff_id=? and (bio is null or trim(bio)='' or binary bio=binary ?)",
                copy.biography((String) row[1], specialty, member),
                row[0],
                original
            )
        );
    }

    private void update(String table, String code, String content, String original) {
        // Table names are private hard-coded constants; only unchanged seeded text or blanks are replaced.
        String previousCopy = table.equals("services")
            ? content.replace("phí khám theo một lượt", "phí khám mẫu theo một lượt")
            : content;
        jdbc.update(
            "update " +
                table +
                " set description=? where code=? and (description is null or trim(description)='' or binary description=binary ? or binary description=binary ?)",
            content,
            code,
            original,
            previousCopy
        );
    }

    static String photo(int index, int member) {
        return DemoPortraitSeederImpl.path(index * 3 + member + 3);
    }

    private void photo(String username, String path) {
        jdbc.update(
            "update doctor_profiles dp join staff s on s.id=dp.staff_id join users u on u.id=s.user_id set dp.photo_url=? where u.username=? and (dp.photo_url is null or trim(dp.photo_url)='')",
            path,
            username
        );
    }
}
