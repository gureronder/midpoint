SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
-- =============================================
-- Author:		lazyman
-- Create date: 15.01.2013
-- Description:	used for db cleanup during testing, used by bamboo build system
-- =============================================
CREATE PROCEDURE cleanupTestDatabaseProc
AS
  BEGIN
    SET NOCOUNT ON;

    DELETE FROM m_acc_cert_campaign;
    DELETE FROM m_acc_cert_definition;
    DELETE FROM m_audit_delta;
    DELETE FROM m_audit_event;
    DELETE FROM m_object_ext_date;
    DELETE FROM m_object_ext_long;
    DELETE FROM m_object_ext_string;
    DELETE FROM m_object_ext_poly;
    DELETE FROM m_object_ext_reference;
    DELETE FROM m_object_ext_boolean;
    DELETE FROM m_reference;
    DELETE FROM m_assignment_ext_date;
    DELETE FROM m_assignment_ext_long;
    DELETE FROM m_assignment_ext_poly;
    DELETE FROM m_assignment_ext_reference;
    DELETE FROM m_assignment_ext_string;
    DELETE FROM m_assignment_ext_boolean;
    DELETE FROM m_assignment_extension;
    DELETE FROM m_assignment_reference;
    DELETE FROM m_assignment;
    DELETE FROM m_exclusion;
    DELETE FROM m_connector_target_system;
    DELETE FROM m_connector;
    DELETE FROM m_connector_host;
    DELETE FROM m_lookup_table_row;
    DELETE FROM m_lookup_table;
    DELETE FROM m_node;
    DELETE FROM m_shadow;
    DELETE FROM m_task_dependent;
    DELETE FROM m_task;
    DELETE FROM m_object_template;
    DELETE FROM m_value_policy;
    DELETE FROM m_resource;
    DELETE FROM m_user_employee_type;
    DELETE FROM m_user_organization;
    DELETE FROM m_user_organizational_unit;
    DELETE FROM m_user_photo;
    DELETE FROM m_user;
    DELETE FROM m_report;
    DELETE FROM m_report_output;
    DELETE FROM m_org_org_type;
    DELETE FROM m_org_closure;
    DELETE FROM m_org;
    DELETE FROM m_role;
    DELETE FROM m_abstract_role;
    DELETE FROM m_system_configuration;
    DELETE FROM m_generic_object;
    DELETE FROM m_trigger;
    DELETE FROM m_focus;
    DELETE FROM m_security_policy;
    DELETE FROM m_object;

    UPDATE hibernate_sequence SET next_val = 1;
  END
GO
