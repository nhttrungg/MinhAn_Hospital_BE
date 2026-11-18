package vn.antam.clinic.service.impl.demo;

import java.util.Map;

/** Original Vietnamese copy for fictional demo profiles, never a real hospital biography. */
final class DemoDirectoryContent {

    private DemoDirectoryContent() {}

    record Content(
        String overview,
        String firstFocus,
        String secondFocus,
        String thirdFocus,
        String consultation,
        String preparation
    ) {
        String focus(int member) {
            return switch (member) {
                case 0 -> firstFocus;
                case 1 -> secondFocus;
                default -> thirdFocus;
            };
        }

        String department(String name) {
            return (
                "Khoa " +
                name +
                " tiếp nhận khám và tư vấn ngoại trú. " +
                overview +
                " Phạm vi: " +
                firstFocus +
                "; " +
                secondFocus +
                "; " +
                thirdFocus +
                ". Phối hợp tiếp nhận, bác sĩ và thu ngân theo lịch đã công bố tại Minh An."
            );
        }

        String specialty(String name) {
            return (
                "Chuyên khoa " +
                name +
                " tiếp nhận người bệnh có nhu cầu khám và theo dõi ngoại trú. " +
                overview +
                "\n\nNội dung thăm khám\n" +
                consultation +
                " Các hướng tiếp nhận gồm " +
                firstFocus +
                "; " +
                secondFocus +
                "; " +
                thirdFocus +
                "." +
                "\n\nChuẩn bị cho buổi khám\n" +
                preparation +
                " Có thể lựa chọn bác sĩ và giờ khám đang mở trên trang đặt lịch."
            );
        }

        String biography(String fullName, String specialty, int member) {
            String approach = switch (member) {
                case 0 -> "Ưu tiên lắng nghe diễn biến triệu chứng và sắp xếp thông tin người bệnh cung cấp trước khi trao đổi kế hoạch theo dõi.";
                case 1 -> "Chú trọng đối chiếu hồ sơ các lần khám, giải thích kết quả được cung cấp và ghi rõ nội dung cần theo dõi ở lần tái khám.";
                default -> "Dành thời gian giải thích nội dung buổi khám, trao đổi các câu hỏi và giúp người bệnh hiểu hướng dẫn đã được ghi trong bệnh án.";
            };
            return (
                "Bác sĩ " +
                fullName +
                " khám và tư vấn chuyên khoa " +
                specialty +
                ", tập trung vào " +
                focus(member) +
                "." +
                "\n\nNội dung tư vấn\n" +
                consultation +
                "\n\nCách tiếp cận người bệnh\n" +
                approach +
                "\n\nTrước khi đến khám\n" +
                preparation +
                " Mang theo mã lịch hẹn để làm thủ tục tiếp nhận tại Minh An."
            );
        }

        String room(String name) {
            return (
                "Phòng khám " +
                name +
                " dành cho lượt khám ngoại trú đã được xếp lịch, mỗi khoảng tiếp nhận một người bệnh. " +
                "Nội dung làm việc: " +
                consultation +
                "\n\nNgười bệnh làm thủ tục tại lễ tân và hoàn tất phí khám trước khi vào bàn khám. " +
                "Phòng được dùng luân phiên theo lịch của bác sĩ; thời gian, bác sĩ và phòng cụ thể hiển thị trên lịch hẹn. " +
                "Vui lòng đối chiếu thông tin phòng trên lịch hẹn khi đến cơ sở."
            );
        }

        String service(String name) {
            return (
                "Một lượt khám ngoại trú chuyên khoa " +
                name +
                ", gồm trao đổi lý do khám, xem thông tin sức khỏe đã cung cấp và tư vấn theo kết quả thăm khám. " +
                consultation +
                "\n\nKết quả buổi khám được ghi trong bệnh án; đơn thuốc được lập khi bác sĩ thấy phù hợp. " +
                "Giá hiển thị là phí khám theo một lượt, không bao gồm tiền thuốc, xét nghiệm hoặc thủ thuật. " +
                "Thuốc trong đơn được mua ngoài. " +
                "\n\nChuẩn bị\n" +
                preparation
            );
        }
    }

    static Content forCode(String code) {
        Content content = CONTENT.get(code);
        if (content == null) throw new IllegalArgumentException("Missing demo copy: " + code);
        return content;
    }

    private static final Map<String, Content> CONTENT = Map.ofEntries(
        Map.entry(
            "TIM",
            new Content(
                "Tập trung các vấn đề tim mạch và nhu cầu theo dõi sức khỏe tim.",
                "huyết áp và sức khỏe tim",
                "theo dõi hồ sơ tim mạch",
                "tư vấn thói quen sinh hoạt",
                "Trao đổi về huyết áp, hồi hộp, khả năng vận động và hồ sơ tim mạch; tổng hợp thông tin để lập hướng theo dõi.",
                "Mang sổ theo dõi huyết áp nếu có, đơn thuốc đang dùng và kết quả khám tim mạch trước đây."
            )
        ),
        Map.entry(
            "TH",
            new Content(
                "Tiếp nhận nhu cầu khám tiêu hóa, gan mật và tái khám theo hồ sơ.",
                "triệu chứng dạ dày và ruột",
                "theo dõi hồ sơ gan mật",
                "tư vấn sinh hoạt và ăn uống",
                "Ghi nhận vị trí, thời điểm khó chịu ở bụng, thay đổi tiêu hóa và các kết quả đã có; trao đổi hướng theo dõi sau khám.",
                "Mang kết quả nội soi, xét nghiệm hoặc siêu âm đã có cùng danh sách thuốc; ghi lại diễn biến vấn đề tiêu hóa."
            )
        ),
        Map.entry(
            "NT",
            new Content(
                "Hỗ trợ khám nội tiết, đường huyết và theo dõi các hồ sơ tuyến giáp.",
                "theo dõi đường huyết",
                "hồ sơ tuyến giáp",
                "tư vấn theo dõi nội tiết",
                "Tổng hợp triệu chứng, nhật ký đường huyết và hồ sơ nội tiết; trao đổi việc theo dõi, lịch tái khám và câu hỏi của người bệnh.",
                "Mang nhật ký đường huyết nếu có, kết quả xét nghiệm gần đây và thông tin thuốc đang sử dụng."
            )
        ),
        Map.entry(
            "HH",
            new Content(
                "Dành cho nhu cầu khám đường hô hấp và theo dõi triệu chứng kéo dài.",
                "ho và triệu chứng hô hấp",
                "theo dõi hồ sơ hô hấp",
                "tư vấn môi trường sinh hoạt",
                "Trao đổi về thời gian ho, mức độ khó chịu, yếu tố môi trường và tiền sử hô hấp; ghi nhận tình trạng để hướng dẫn theo dõi.",
                "Mang đơn thuốc, phim hoặc kết quả hô hấp đã có; ghi lại thời điểm triệu chứng xuất hiện và yếu tố liên quan."
            )
        ),
        Map.entry(
            "CXK",
            new Content(
                "Tiếp nhận vấn đề cơ, khớp và ảnh hưởng đến vận động hằng ngày.",
                "đau khớp và cứng khớp",
                "theo dõi hồ sơ cơ xương khớp",
                "tư vấn vận động sinh hoạt",
                "Ghi nhận vị trí đau, thời gian cứng khớp và giới hạn vận động; xem hồ sơ đã có và trao đổi mục tiêu theo dõi sau khám.",
                "Mang kết quả chụp hoặc đơn thuốc trước đây; chuẩn bị thông tin về vị trí đau và hoạt động bị ảnh hưởng."
            )
        ),
        Map.entry(
            "TK",
            new Content(
                "Khám ngoại trú các vấn đề thần kinh và nhu cầu theo dõi theo hồ sơ.",
                "đau đầu và chóng mặt",
                "theo dõi triệu chứng thần kinh",
                "tư vấn giấc ngủ và sinh hoạt",
                "Khai thác diễn biến đau đầu, cảm giác tê hoặc chóng mặt, giấc ngủ và tiền sử; tổng hợp để trao đổi kế hoạch theo dõi.",
                "Mang kết quả khám hoặc phim đã có, danh sách thuốc và ghi chép thời điểm xuất hiện triệu chứng."
            )
        ),
        Map.entry(
            "TNT",
            new Content(
                "Khám sức khỏe thận, đường tiết niệu và theo dõi hồ sơ liên quan.",
                "triệu chứng đường tiết niệu",
                "theo dõi hồ sơ thận",
                "tư vấn theo dõi sinh hoạt",
                "Trao đổi các thay đổi khi đi tiểu, diễn biến triệu chứng và hồ sơ thận - tiết niệu; giải thích nội dung cần theo dõi sau khám.",
                "Mang xét nghiệm nước tiểu, kết quả siêu âm đã có và đơn thuốc; ghi lại diễn biến triệu chứng để trao đổi với bác sĩ."
            )
        ),
        Map.entry(
            "DL",
            new Content(
                "Tiếp nhận vấn đề da, tóc, móng và nhu cầu tư vấn chăm sóc da.",
                "tổn thương và ngứa da",
                "theo dõi vấn đề da kéo dài",
                "tư vấn thói quen chăm sóc da",
                "Ghi nhận vị trí, thời gian xuất hiện vấn đề trên da và sản phẩm đang dùng; trao đổi cách theo dõi sau buổi khám.",
                "Chuẩn bị tên thuốc, mỹ phẩm đang sử dụng và ảnh diễn biến nếu có; cung cấp thông tin dị ứng đã biết."
            )
        ),
        Map.entry(
            "TMH",
            new Content(
                "Khám các vấn đề tai, mũi, họng và ảnh hưởng đến sinh hoạt.",
                "triệu chứng tai và thính giác",
                "triệu chứng mũi xoang",
                "triệu chứng họng và giọng nói",
                "Trao đổi về nghe, nghẹt mũi, khó chịu ở họng và thời gian kéo dài; đối chiếu hồ sơ cũ và tư vấn theo nội dung thăm khám.",
                "Mang đơn thuốc, kết quả khám tai mũi họng trước đây và thông tin về thời điểm, tần suất triệu chứng."
            )
        ),
        Map.entry(
            "MAT",
            new Content(
                "Khám sức khỏe mắt, thị lực và nhu cầu tư vấn chăm sóc mắt.",
                "khó chịu ở mắt",
                "theo dõi hồ sơ thị lực",
                "tư vấn thói quen sử dụng mắt",
                "Ghi nhận thay đổi thị lực, khó chịu ở mắt và nhu cầu sinh hoạt; trao đổi hồ sơ đã có và nội dung theo dõi sau khám.",
                "Mang kính đang dùng, đơn kính hoặc kết quả khám mắt cũ; chuẩn bị thông tin thuốc nhỏ mắt đang sử dụng."
            )
        ),
        Map.entry(
            "RHM",
            new Content(
                "Khám sức khỏe răng miệng và tư vấn chăm sóc theo tình trạng cá nhân.",
                "triệu chứng răng và lợi",
                "đánh giá hồ sơ răng miệng",
                "tư vấn vệ sinh răng miệng",
                "Trao đổi vị trí khó chịu, lịch sử điều trị răng và thói quen vệ sinh; đánh giá nhu cầu tư vấn, không mặc định kèm thủ thuật.",
                "Mang phim răng hoặc hồ sơ điều trị nếu có; cung cấp thuốc đang dùng, thông tin dị ứng và vấn đề cần hỏi."
            )
        ),
        Map.entry(
            "SP",
            new Content(
                "Khám phụ khoa và trao đổi nhu cầu chăm sóc sức khỏe sinh sản.",
                "triệu chứng phụ khoa",
                "theo dõi hồ sơ sức khỏe sinh sản",
                "tư vấn chăm sóc theo giai đoạn",
                "Trao đổi triệu chứng, tiền sử và nhu cầu sức khỏe sinh sản trong không gian riêng tư; ghi nhận kế hoạch theo dõi sau khám.",
                "Mang hồ sơ phụ khoa hoặc thai kỳ đã có; chuẩn bị thông tin chu kỳ, thuốc đang dùng và câu hỏi muốn trao đổi."
            )
        ),
        Map.entry(
            "NHI",
            new Content(
                "Dành cho trẻ em và người chăm sóc cần khám, theo dõi sức khỏe ngoại trú.",
                "triệu chứng thường gặp ở trẻ",
                "theo dõi hồ sơ phát triển",
                "tư vấn cho người chăm sóc",
                "Trao đổi với người chăm sóc về triệu chứng, ăn ngủ, sinh hoạt và hồ sơ phát triển của trẻ; ghi hướng dẫn theo dõi rõ ràng.",
                "Người chăm sóc mang sổ theo dõi sức khỏe, thông tin tiêm chủng đã có, thuốc đang dùng và ghi chép diễn biến của trẻ."
            )
        ),
        Map.entry(
            "UB",
            new Content(
                "Tư vấn và theo dõi ngoại trú theo hồ sơ chuyên khoa ung bướu đã có.",
                "tư vấn theo hồ sơ ung bướu",
                "theo dõi sau các lần điều trị",
                "trao đổi nhu cầu chăm sóc",
                "Sắp xếp hồ sơ chẩn đoán, điều trị đã có và câu hỏi của người bệnh; trao đổi nhu cầu chăm sóc và kế hoạch theo dõi ngoại trú.",
                "Mang hồ sơ chẩn đoán, tóm tắt điều trị, kết quả gần đây và danh sách thuốc để bác sĩ có thông tin đối chiếu."
            )
        ),
        Map.entry(
            "NAM",
            new Content(
                "Khám, tư vấn sức khỏe nam khoa với trao đổi riêng tư theo nhu cầu.",
                "triệu chứng nam khoa",
                "theo dõi hồ sơ sức khỏe nam giới",
                "tư vấn sức khỏe sinh sản nam",
                "Khai thác vấn đề cần tư vấn, diễn biến và hồ sơ liên quan; trao đổi trực tiếp, tôn trọng sự riêng tư của người đến khám.",
                "Mang hồ sơ khám, xét nghiệm đã có và thông tin thuốc; chuẩn bị các câu hỏi muốn trao đổi riêng với bác sĩ."
            )
        ),
        Map.entry(
            "CTCH",
            new Content(
                "Khám vấn đề vận động sau chấn thương và theo dõi hồ sơ chỉnh hình.",
                "đánh giá sau chấn thương",
                "theo dõi hồ sơ chỉnh hình",
                "tư vấn trở lại sinh hoạt",
                "Ghi nhận cơ chế chấn thương, vị trí đau và hạn chế vận động; đối chiếu hồ sơ, trao đổi mục tiêu theo dõi ngoại trú.",
                "Mang phim, tóm tắt điều trị hoặc giấy hẹn đã có; ghi lại thời điểm chấn thương và hoạt động đang bị hạn chế."
            )
        ),
        Map.entry(
            "PHCN",
            new Content(
                "Đánh giá khả năng vận động và nhu cầu phục hồi trong sinh hoạt.",
                "đánh giá chức năng vận động",
                "theo dõi tiến trình phục hồi",
                "tư vấn mục tiêu sinh hoạt",
                "Trao đổi về hoạt động đang khó thực hiện, mục tiêu cá nhân và hồ sơ điều trị; ghi nhận kế hoạch tư vấn theo khả năng hiện tại.",
                "Mang hồ sơ điều trị, kế hoạch phục hồi đã có; chuẩn bị danh sách hoạt động cần hỗ trợ và dụng cụ đang dùng."
            )
        ),
        Map.entry(
            "DD",
            new Content(
                "Tư vấn chế độ ăn theo nhu cầu, thói quen và hồ sơ sức khỏe.",
                "đánh giá thói quen ăn uống",
                "theo dõi hồ sơ dinh dưỡng",
                "tư vấn mục tiêu dinh dưỡng",
                "Tổng hợp khẩu phần, sinh hoạt, mục tiêu cân nặng và hồ sơ sức khỏe; trao đổi các mục tiêu ăn uống phù hợp để theo dõi.",
                "Chuẩn bị ghi chép bữa ăn gần đây, thay đổi cân nặng và hồ sơ sức khỏe; cung cấp thông tin dị ứng thực phẩm nếu có."
            )
        ),
        Map.entry(
            "NOI",
            new Content(
                "Khám sức khỏe tổng quát và xem xét nhiều vấn đề nội khoa trong một lượt khám.",
                "đánh giá triệu chứng nội khoa",
                "theo dõi hồ sơ sức khỏe",
                "tư vấn khám theo nhu cầu",
                "Tổng hợp lý do đến khám, tiền sử, dị ứng và thuốc đang dùng; trao đổi tình trạng sức khỏe và nhu cầu khám chuyên khoa tiếp theo.",
                "Mang hồ sơ khám, kết quả gần đây và đơn thuốc; chuẩn bị danh sách vấn đề sức khỏe muốn trao đổi."
            )
        ),
        Map.entry(
            "NGOAI",
            new Content(
                "Khám ngoại tổng quát và tư vấn theo hồ sơ điều trị ngoại khoa.",
                "triệu chứng ngoại khoa",
                "theo dõi sau điều trị ngoại khoa",
                "tư vấn chăm sóc theo hồ sơ",
                "Khai thác diễn biến vấn đề ngoại khoa, xem hồ sơ đã có và trao đổi kế hoạch theo dõi; lượt khám không bao gồm phẫu thuật.",
                "Mang giấy hẹn, tóm tắt điều trị, phim hoặc kết quả đã có cùng thông tin thuốc đang dùng."
            )
        )
    );
}
