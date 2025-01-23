------------- Application Parameters --------------------------------------------
-- How many minutes we keep data for


UPSERT INTO application_parameters 
(parameter_name, parameter_value)
VALUES
('KEEP_MINUTES','10');

