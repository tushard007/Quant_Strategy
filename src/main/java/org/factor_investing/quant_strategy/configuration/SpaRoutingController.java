package org.factor_investing.quant_strategy.configuration;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaRoutingController {

    @GetMapping({
            "/login",
            "/overview/{page}",
            "/analyze/{page}",
            "/backtest/{page}",
            "/data/{page}",
            "/administration/{page}"
    })
    public String forwardApplicationRoute() {
        return "forward:/index.html";
    }
}
