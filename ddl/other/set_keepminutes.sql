--
-- Copyright (C) 2025 Volt Active Data Inc.
--
-- Use of this source code is governed by an MIT
-- license that can be found in the LICENSE file or at
-- https://opensource.org/licenses/MIT.
--

------------- Application Parameters --------------------------------------------
-- How many minutes we keep data for


UPSERT INTO application_parameters 
(parameter_name, parameter_value)
VALUES
('KEEP_MINUTES','10');

