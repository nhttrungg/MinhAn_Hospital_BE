package vn.antam.clinic.web.rest.booking;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import vn.antam.clinic.service.booking.WaitlistService;
import vn.antam.clinic.service.dto.booking.WaitlistDtos.*;

@RestController
@RequestMapping("/api/v1/waitlist")
public class WaitlistResource {

    private final WaitlistService waitlist;

    public WaitlistResource(WaitlistService waitlist) {
        this.waitlist = waitlist;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Entry join(@Valid @RequestBody JoinRequest input) {
        return waitlist.join(input);
    }

    @GetMapping("/me")
    public List<Entry> mine() {
        return waitlist.mine();
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable long id) {
        waitlist.cancel(id);
    }
}
